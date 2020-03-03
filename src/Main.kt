import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.image.*
import java.util.*
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

const val INTERVAL_MILLIS: Long = 100
const val SKIP_FRAME_COUNT = 0
const val INITIAL_DENSITY = 0.375f
const val WHEEL_INTERVAL_STEP = 5

fun main() {
    val automata = CellularAutomata(100, 100, ::randInit)
    val monitor = CellularMonitor(automata, scale = 6)
    monitor.show()
    while (true) {
        monitor.update()
    }
}

private var seed = System.nanoTime()
private val rand = Random().apply { setSeed(seed) }

fun reuseSeed() = rand.setSeed(seed)
fun newSeed() {
    seed = System.nanoTime()
    rand.setSeed(seed)
}


private fun randInit(data: BooleanArray) {
    for (i in data.indices) {
        data[i] = rand.nextFloat() < INITIAL_DENSITY
    }
}

class CellularMonitor(
    private val automata: CellularAutomata,
    private val scale: Int,
    backColor: Color = Color.LIGHT_GRAY,
    deadColor: Color = Color.WHITE,
    aliveColor: Color = Color.BLACK
) {

    private val width = automata.width
    private val height = automata.height
    private val imageWidth = width * scale
    private val imageHeight = height * scale
    private val data = ByteArray(imageWidth * imageHeight)
    private val image: BufferedImage

    init {
        val colorModel = IndexColorModel(
            8, 3, intArrayOf(backColor.rgb, deadColor.rgb, aliveColor.rgb),
            0, false, -1, DataBuffer.TYPE_BYTE
        )
        val dataBuffer = DataBufferByte(data, data.size)
        val raster = Raster.createInterleavedRaster(
            dataBuffer, imageWidth, imageHeight, imageWidth, 1, intArrayOf(0), null
        )
        image = BufferedImage(colorModel, raster, true, null)
    }

    var paused = false
        internal set(v) {
            if (v) {
                lastTime = 0
                frame.title = "Conway's Game of Life: PAUSED"
            }
            field = v
        }

    private var interval = INTERVAL_MILLIS
        private set(v) {
            field = if (v > 0) v else 0
        }

    private val label = JLabel().apply {
        icon = ImageIcon(image)
        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON2) {
                        if (e.modifiersEx and MouseEvent.BUTTON3_DOWN_MASK != 0) {
                            reuseSeed()
                            automata.init()
                        }
                        paused = !paused
                    } else if (paused && e.button == MouseEvent.BUTTON1) {
                        val x = e.x / scale
                        val y = e.y / scale
                        switch(x, y)
                    }
                }

                override fun mousePressed(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON3) {
                        paused = true
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON3) {
                        if (paused) {
                            newSeed()
                            automata.init()
                            paused = false
                        }
                    }
                }
            }
        )
        addMouseMotionListener(
            object : MouseMotionAdapter() {
                var lastX: Int = -1
                var lastY: Int = -1

                override fun mouseDragged(e: MouseEvent) {
                    if (paused && e.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK != 0) {
                        val x = e.x / scale
                        val y = e.y / scale
                        if (x != lastX || y != lastY) {
                            switch(x, y)
                            lastX = x
                            lastY = y
                        }
                    }
                }
            }
        )
        addMouseWheelListener { e: MouseWheelEvent ->
            if (e.wheelRotation > 0)
                interval += WHEEL_INTERVAL_STEP
            else interval -= WHEEL_INTERVAL_STEP
        }
    }

    private fun switch(x: Int, y: Int) {
        if (x < 0 || x >= width || y < 0 || y >= height)
            return
        val v = !automata.get(x, y)
        automata.set(x, y, v)
        fillRect((y * imageWidth + x) * scale, v)
        label.repaint()
    }

    private val frame = JFrame().apply {
        title = "Conway's Game of Life"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isResizable = false
        add(label)
        pack()
    }
    private var lastTime = 0L
    private var frameCnt = 0
    private var skipCnt = 0

    fun update() {
        if (paused) {
            Thread.sleep(10)
            return
        }
        automata.update()
        if (skipCnt == SKIP_FRAME_COUNT) {
            Thread.sleep(interval)
            draw()
            label.repaint()
            skipCnt = 0
        } else skipCnt++
        val curTime = System.currentTimeMillis()
        if (curTime - lastTime >= 1000) {
            if (lastTime > 0)
                frame.title = "Conway's Game of Life: $frameCnt fps"
            lastTime = curTime
            frameCnt = 0
        } else frameCnt++
    }

    private val delta = scale * imageWidth

    private fun draw() {
        var i = 0
        for (y in 0 until height) {
            var i0 = i
            for (x  in 0 until width) {
                fillRect(i0, automata.get(x, y))
                i0 += scale
            }
            i += delta
        }
    }

    private val cellularSize = if (scale == 1) 1 else scale - 1

    private fun fillRect(start: Int, b: Boolean) {
        val v: Byte = if (b) 2 else 1
        var k = start
        for (i in 0 until cellularSize) {
            for (j in 0 until cellularSize) {
                data[k + j] = v
            }
            k += imageWidth
        }
    }

    fun show() {
        draw()
        frame.isVisible = true
    }
}

class CellularAutomata(
    val width: Int,
    val height: Int,
    private val dataInitializer: ((data: BooleanArray) -> Unit)? = null
) {

    private var data = BooleanArray(width * height)
    private var nextData = BooleanArray(width * height)
    init { dataInitializer?.invoke(data) }

    fun update() {
        for (y in 0 until height) {
            val i = y * width
            for (x in 0 until width) {
                val cnt = cntNeighbors(x, y)
                var b = get(x, y)
                if (b) {
                    if (cnt != 2 && cnt != 3)
                        b = false
                } else if (cnt == 3) b = true
                nextData[i + x] = b
            }
        }
        val tmp = data
        data = nextData
        nextData = tmp
    }

    private fun cntNeighbors(x: Int, y: Int): Int =
        cnt(x - 1, y - 1) + cnt(x, y - 1) +
                cnt(x + 1, y - 1) + cnt(x - 1, y) +
                cnt(x + 1, y) + cnt(x - 1, y + 1) +
                cnt(x, y + 1) + cnt(x + 1, y + 1)

    private fun cnt(x: Int, y: Int): Int {
        val x0 = when {
            x < 0 -> width - 1
            x == width -> 0
            else -> x
        }
        val y0 = when {
            y < 0 -> height - 1
            y == height -> 0
            else -> y
        }
        return if (get(x0, y0)) 1 else 0
    }

    fun get(x: Int, y: Int) = data[y * width + x]

    internal fun set(x: Int, y: Int, b: Boolean) {
        data[y * width + x] = b
    }

    internal fun init() {
        dataInitializer?.invoke(data)
            ?: data.fill(false)
    }
}