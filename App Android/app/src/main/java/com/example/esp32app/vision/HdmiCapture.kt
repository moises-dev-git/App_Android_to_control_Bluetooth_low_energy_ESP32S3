package com.example.esp32app.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.usb.USBMonitor

/**
 * Captura da saída HDMI do PC através de uma placa de captura USB (UVC).
 *
 * É um singleton de aplicação: a captura continua ativa entre telas para que as
 * macros possam consultar a tela enquanto executam. Não há preview em View; a
 * libausbc renderiza fora da tela (pbuffer) e entrega os frames YUV brutos.
 */
object HdmiCapture {

    private const val TAG = "HdmiCapture"
    private const val PREVIEW_WIDTH = 1920
    private const val PREVIEW_HEIGHT = 1080

    /** Frames mais antigos que isso são considerados "sem sinal". */
    const val STALE_FRAME_MS = 2_000L

    /**
     * Placas como a MS2130 se conectam, somem ~150 ms depois e reaparecem com outro endereço
     * (/dev/bus/usb/x/2 -> x/3). Só abrimos a placa depois que ela fica estável por esse tempo.
     */
    private const val SETTLE_MS = 2_500L

    /**
     * No Android 14+ a resposta do pedido de permissão USB chega sem o dispositivo
     * (PendingIntent imutável) e a libausbc a descarta. Então consultamos a permissão
     * periodicamente até ela ser concedida.
     */
    private const val PERMISSION_POLL_MS = 500L
    private const val PERMISSION_TIMEOUT_MS = 60_000L

    enum class Status { STOPPED, NO_DEVICE, WAITING_PERMISSION, OPENING, STREAMING, ERROR }

    val statusState = mutableStateOf(Status.STOPPED)
    val messageState = mutableStateOf("Captura parada")
    val resolutionState = mutableStateOf<Pair<Int, Int>?>(null)

    @Volatile
    var latestFrame: Frame? = null
        private set

    private var client: MultiCameraClient? = null
    private var camera: CameraUVC? = null
    private var usbManager: UsbManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Placa detectada, aguardando estabilizar e/ou permissão. Acessado só na main thread. */
    private var pendingDevice: UsbDevice? = null
    private var settling = false
    private var waitingPermission = false
    private var permissionDeadline = 0L

    private val settleRunnable = Runnable { onDeviceSettled() }
    private val permissionPollRunnable = object : Runnable {
        override fun run() = pollPermission(this)
    }

    @Volatile
    private var streamingNotified = false

    private val frameCallback = object : IPreviewDataCallBack {
        override fun onPreviewData(data: ByteArray?, width: Int, height: Int, format: IPreviewDataCallBack.DataFormat) {
            if (data == null || format != IPreviewDataCallBack.DataFormat.NV21) return
            latestFrame = Frame(data, width, height)
            if (!streamingNotified) {
                streamingNotified = true
                setStatus(Status.STREAMING, "Recebendo imagem ${width}x$height", Pair(width, height))
            }
        }
    }

    fun hasCameraPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** true se há um frame recente. */
    fun hasFreshFrame(): Boolean {
        val frame = latestFrame ?: return false
        return System.currentTimeMillis() - frame.timestampMs < STALE_FRAME_MS
    }

    /**
     * Começa a monitorar a USB. Ao detectar a placa, pede permissão de USB e abre a captura.
     * Requer a permissão CAMERA já concedida (exigência da libausbc para targetSdk >= 28).
     * Os callbacks da libausbc chegam na main thread.
     */
    fun start(context: Context) {
        if (client != null) return
        val appContext = context.applicationContext
        if (!hasCameraPermission(appContext)) {
            setStatus(Status.ERROR, "Permissão de câmera não concedida")
            return
        }
        usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        setStatus(Status.NO_DEVICE, "Conecte a placa de captura HDMI na USB do celular")
        client = MultiCameraClient(appContext, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                if (camera != null) return
                // A libausbc repete onAttach periodicamente; não reinicia uma espera em andamento
                if (pendingDevice?.deviceName == device.deviceName && (settling || waitingPermission)) return
                scheduleSettle(device)
            }

            override fun onDetachDec(device: UsbDevice?) {
                device ?: return
                if (pendingDevice?.deviceName == device.deviceName) cancelPending()
                if (camera?.getUsbDevice()?.deviceName == device.deviceName) closeCamera()
                if (camera == null && pendingDevice == null) {
                    setStatus(Status.NO_DEVICE, "Placa de captura desconectada")
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                if (device == null || ctrlBlock == null || camera != null) return
                // Ignora a conexão automática da libausbc durante a reconexão inicial da placa;
                // depois de estabilizar, pedimos a permissão de novo e ela reconecta.
                if (settling || !isAttached(device)) {
                    Log.i(TAG, "Ignorando conexão de ${device.deviceName} (placa ainda instável)")
                    return
                }
                cancelPending()
                openCamera(appContext, device, ctrlBlock)
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                if (device != null && camera?.getUsbDevice()?.deviceName == device.deviceName) {
                    closeCamera()
                    setStatus(Status.NO_DEVICE, "Placa de captura desconectada")
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                cancelPending()
                setStatus(Status.ERROR, "Acesso USB negado à placa de captura")
            }
        }).also { it.register() }
    }

    fun stop() {
        cancelPending()
        closeCamera()
        client?.unRegister()
        client?.destroy()
        client = null
        setStatus(Status.STOPPED, "Captura parada")
    }

    private fun scheduleSettle(device: UsbDevice) {
        cancelPending()
        pendingDevice = device
        settling = true
        setStatus(Status.NO_DEVICE, "Placa detectada: ${device.productName ?: device.deviceName}. Aguardando estabilizar...")
        mainHandler.postDelayed(settleRunnable, SETTLE_MS)
    }

    private fun onDeviceSettled() {
        settling = false
        val device = pendingDevice?.let { findAttached(it) }
        if (device == null) {
            pendingDevice = null
            return
        }
        pendingDevice = device
        if (usbManager?.hasPermission(device) == true) {
            // Com permissão, requestPermission da libausbc conecta direto (-> onConnectDev)
            client?.requestPermission(device)
        } else {
            setStatus(Status.WAITING_PERMISSION, "Autorize o acesso USB à placa de captura")
            client?.requestPermission(device)
            waitingPermission = true
            permissionDeadline = System.currentTimeMillis() + PERMISSION_TIMEOUT_MS
            mainHandler.postDelayed(permissionPollRunnable, PERMISSION_POLL_MS)
        }
    }

    private fun pollPermission(self: Runnable) {
        if (!waitingPermission) return
        val device = pendingDevice?.let { findAttached(it) }
        when {
            device == null -> cancelPending()
            usbManager?.hasPermission(device) == true -> {
                waitingPermission = false
                Log.i(TAG, "Permissão USB concedida para ${device.deviceName}")
                client?.requestPermission(device)
            }
            System.currentTimeMillis() > permissionDeadline -> {
                cancelPending()
                setStatus(Status.ERROR, "Acesso USB não autorizado. Toque em \"Tentar novamente\".")
            }
            else -> mainHandler.postDelayed(self, PERMISSION_POLL_MS)
        }
    }

    private fun cancelPending() {
        mainHandler.removeCallbacks(settleRunnable)
        mainHandler.removeCallbacks(permissionPollRunnable)
        settling = false
        waitingPermission = false
        pendingDevice = null
    }

    /** Versão atual do dispositivo na lista do sistema (mesmo endereço), ou null se sumiu. */
    private fun findAttached(device: UsbDevice): UsbDevice? =
        usbManager?.deviceList?.values?.firstOrNull { it.deviceName == device.deviceName }

    private fun isAttached(device: UsbDevice) = findAttached(device) != null

    private fun openCamera(context: Context, device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        setStatus(Status.OPENING, "Abrindo captura...")
        val request = CameraRequest.Builder()
            .setPreviewWidth(PREVIEW_WIDTH)
            .setPreviewHeight(PREVIEW_HEIGHT)
            // Sem superfície, a libausbc renderiza fora da tela; rawPreviewData entrega YUV direto
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setRawPreviewData(true)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setAspectRatioShow(false)
            .create()
        camera = CameraUVC(context, device).apply {
            setUsbControlBlock(ctrlBlock)
            setCameraStateCallBack(object : ICameraStateCallBack {
                override fun onCameraState(self: MultiCameraClient.ICamera, code: ICameraStateCallBack.State, msg: String?) {
                    // Eventos de uma câmera já fechada (ex.: placa que reconectou) não valem mais
                    if (self !== camera) return
                    when (code) {
                        ICameraStateCallBack.State.OPENED -> setStatus(Status.OPENING, "Captura aberta, aguardando imagem...")
                        ICameraStateCallBack.State.CLOSED -> Unit
                        ICameraStateCallBack.State.ERROR -> {
                            Log.e(TAG, "Erro na captura: $msg")
                            setStatus(Status.ERROR, "Erro na captura: ${msg ?: "desconhecido"}")
                        }
                    }
                }
            })
            addPreviewDataCallBack(frameCallback)
            openCamera<Any>(null, request)
        }
    }

    private fun closeCamera() {
        camera?.let {
            it.removePreviewDataCallBack(frameCallback)
            it.closeCamera()
        }
        camera = null
        latestFrame = null
        streamingNotified = false
        mainHandler.post { resolutionState.value = null }
    }

    /** Callbacks da libausbc chegam em threads diversas; o estado do Compose é atualizado na main. */
    private fun setStatus(status: Status, message: String, resolution: Pair<Int, Int>? = null) {
        mainHandler.post {
            statusState.value = status
            messageState.value = message
            if (resolution != null) resolutionState.value = resolution
        }
    }
}
