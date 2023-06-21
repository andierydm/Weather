package gui

import java.awt.Dimension
import javax.swing.*

class JDAbout : JDialog() {
    private val jlMessage = JLabel("")

    init {
        jlMessage.text = """
            <html>
                <h1>Weather</h1>
                <br>
                <p>
                    Thanks to <a href='https://simplemaps.com'>simplemaps</a> for provide, freely, the<br>
                    database of the coordinates of all the cities this program use to retrieve the weather for.<br><br>
                    This program use <a href='https://open-meteo.com/'>open-meteo</a> to retrieve the weather information about the specified city or coordinates.
                </p>
            </html>
            """.trimIndent()

        size = Dimension(350, 300)
        isResizable = false

        createLayout()

        title = "About Weather"
        defaultCloseOperation = DISPOSE_ON_CLOSE
    }

    private fun createLayout() {
        val gl = GroupLayout(contentPane)
        contentPane.layout = gl

        gl.autoCreateGaps = true
        gl.autoCreateContainerGaps = true

        gl.setHorizontalGroup(
            gl.createParallelGroup()
                .addGroup(
                    GroupLayout.Alignment.CENTER,
                    gl.createSequentialGroup()
                        .addComponent(jlMessage)
                )
        )

        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlMessage)
                )
        )
    }
}