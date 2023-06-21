package gui

import kotlinx.coroutines.*
import types.Country
import util.Left
import util.Right
import util.celsiusToFahrenheit
import weather.OpenMeteoWeather
import java.awt.event.ItemEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultComboBoxModel
import javax.swing.GroupLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class JPTemperaturesByCountry : JPanel() {
    private val countriesModel = DefaultComboBoxModel<Country>()
    private val temperaturesModel = DefaultTableModel(arrayOf("City", "Admin", "Temperature"), 0)

    private val jlCountry = JLabel("Country:")
    private val jcbCountries = JComboBox(countriesModel)
    private val jbFetch = JButton("Fetch")
    private val jtTemperatures = JTable(temperaturesModel)
    private val jspTemperatures = JScrollPane(jtTemperatures)
    private val jpbTemperatures = JProgressBar()

    private var isWorking = false
    private var shouldStop = AtomicBoolean(false)
    private val weather = OpenMeteoWeather()

    init {
        initListeners()
        createLayout()
    }

    private fun initListeners() {
        jcbCountries.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) temperaturesModel.rowCount = 0
        }

        jbFetch.addActionListener {
            if (isWorking) {
                shouldStop.set(true)
                return@addActionListener
            }

            val country = jcbCountries.selectedItem as? Country
            if (country == null) {
                JOptionPane.showMessageDialog(
                    this,
                    "Could not retrieve temperatures: no country"
                )

                return@addActionListener
            }

            getTempStatically(country)
        }
    }

    private fun createLayout() {
        val gl = GroupLayout(this)
        layout = gl

        gl.autoCreateGaps = true
        gl.autoCreateContainerGaps = true

        gl.setHorizontalGroup(
            gl.createParallelGroup()
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlCountry)
                        .addComponent(
                            jcbCountries,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE,
                            Short.MAX_VALUE.toInt()
                        )
                        .addComponent(jbFetch)
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jspTemperatures)
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jpbTemperatures)
                )
        )

        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlCountry)
                        .addComponent(jcbCountries, 25, 25, 25)
                        .addComponent(jbFetch)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(
                            jspTemperatures,
                            350,
                            350,
                            350
                        )
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jpbTemperatures, 25, 25, 25)
                )
        )
    }

    private fun getTempStatically(country: Country) {
        if (isWorking) return

        setInFetchMode(true)
        jpbTemperatures.maximum = country.cities.size

        val handler = CoroutineExceptionHandler { _, throwable ->
            setInFetchMode(false)

            JOptionPane.showMessageDialog(
                null,
                "Error while trying to get all temperatures. Possible incomplete results. ${throwable.message}"
            )
        }

        CoroutineScope(Dispatchers.IO).launch(handler) {
            val temperatures = mutableListOf<CityTemperature>()

            for ((counter, city) in country.cities.withIndex()) {
                if (shouldStop.get()) {
                    setInFetchMode(false)

                    JOptionPane.showMessageDialog(
                        null,
                        "The process was stopped. It's possible that the result are incomplete"
                    )

                    break
                }

                when (val result = weather.update(city.latitude, city.longitude)) {
                    is Right -> {}

                    is Left -> {
                        setInFetchMode(false)

                        JOptionPane.showMessageDialog(
                            null,
                            "Could not retrieve temperatures. ${result.error.message}"
                        )

                        return@launch
                    }
                }

                temperatures.add(CityTemperature(city.name, city.admin, weather.getTemperature()))
                withContext(Dispatchers.Main) { jpbTemperatures.value = counter }
            }

            temperatures.sortBy { it.temperature }

            withContext(Dispatchers.Main) {
                temperatures.forEach {
                    temperaturesModel.addRow(
                        arrayOf(
                            it.city,
                            it.admin,
                            "${it.temperature} °C    -    ${celsiusToFahrenheit(it.temperature)} °F"
                        )
                    )
                }
                setInFetchMode(false)
            }
        }
    }

    private fun setInFetchMode(active: Boolean) {
        if (active) {
            shouldStop.set(false)
            jbFetch.text = "Cancel"
            temperaturesModel.rowCount = 0
            jpbTemperatures.minimum = 0
            jpbTemperatures.value = 0
        } else {
            jbFetch.text = "Fetch"
            jpbTemperatures.value = 0
            jpbTemperatures.maximum = 0
        }

        isWorking = active
        jcbCountries.isEnabled = !active
        jpbTemperatures.isStringPainted = active
    }

    fun setCountries(countries: List<Country>) {
        countriesModel.addAll(countries)
        jcbCountries.selectedIndex = 0
    }

    fun deactivateInterface(activate: Boolean) {
        jbFetch.isEnabled = activate
        jcbCountries.isEnabled = activate
        jtTemperatures.isEnabled = activate
        jspTemperatures.isEnabled = activate
        jpbTemperatures.isEnabled = activate
    }
}

private data class CityTemperature(
    val city: String,
    val admin: String,
    val temperature: Double
)