package gui

import database.LocationsDatabase
import kotlinx.coroutines.*
import okio.IOException
import types.City
import types.Country
import util.*
import weather.OpenMeteoWeather
import weather.TemperatureUnit
import java.awt.Color
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.event.ItemEvent
import java.time.LocalDateTime
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder

class AppGUI : JFrame() {
    private val jmbWeather = JMenuBar()
    private val jmHelp = JMenu("Help")
    private val jmiAbout = JMenuItem("About")

    private val jtpWeather = JTabbedPane()

    private val jLTime = JLabel("Time:")
    private val jtfTime = JTextField()
    private val jlFlagImage = JLabel("", JLabel.CENTER)

    //Information JPanel
    private val jlLocation = JLabel("Location:")
    private val jtfLocation = JTextField()
    private val jlCoordinates = JLabel("Coordinates:")
    private val jtfCoordinates = JTextField()
    private val jlTemperature = JLabel("Temperature:")
    private val jtfTemperature = JTextField()
    private val jlWeather = JLabel("Weather:")
    private val jtfWeather = JTextField()

    //FetchBy Panel variables
    private val fetchButtonGroup = ButtonGroup()
    private val citiesModel = DefaultComboBoxModel<City>()
    private val countriesModel = DefaultComboBoxModel<Country>()

    //FetchBy Panel
    private val jlCity = JLabel("City:")
    private val jlFetchBy = JLabel("Fetch By:")
    private val jrbFetchByCity = JRadioButton("City")
    private val jrbFetchByCoordinates = JRadioButton("Coordinates")
    private val jcbCity = JComboBox(citiesModel)
    private val jlCountry = JLabel("Country:")
    private val jcbCountry = JComboBox(countriesModel)
    private val jtfAdmin = JTextField()
    private val jlLatitude = JLabel("Latitude:")
    private val jtfLatitude = JTextField()
    private val jlLongitude = JLabel("Longitude:")
    private val jtfLongitude = JTextField()

    private val jbFetch = JButton("Fetch")
    private val jpbFetch = JProgressBar()

    private val jpWeather = createWeatherPanel()
    private val jpTemperatures = JPTemperaturesByCountry()

    private var working = false
    private val weather = OpenMeteoWeather()
    private var shouldRetrieveFlagImg = false
    private var lastCountryCode = ""
    private val countries = mutableListOf<Country>()

    init {
        initListeners()

        jtpWeather.add("Weather", jpWeather)
        jtpWeather.add("Temperatures", jpTemperatures)

        jMenuBar = jmbWeather

        jmbWeather.add(jmHelp)
        jmHelp.add(jmiAbout)

        fetchButtonGroup.add(jrbFetchByCity)
        fetchButtonGroup.add(jrbFetchByCoordinates)

        jlFlagImage.border = LineBorder(Color.BLACK, 1)

        jtfLocation.isEditable = false
        jtfWeather.isEditable = false
        jtfCoordinates.isEditable = false
        jtfTemperature.isEditable = false
        jtfAdmin.isEnabled = false
        jtfTime.isEditable = false

        jtfLatitude.disabledTextColor = Color.BLACK
        jtfLongitude.disabledTextColor = Color.BLACK
        jtfAdmin.disabledTextColor = Color.BLACK

        jtfTime.horizontalAlignment = SwingConstants.CENTER
        jtfCoordinates.horizontalAlignment = SwingConstants.CENTER
        jtfTemperature.horizontalAlignment = SwingConstants.CENTER
        jtfLocation.horizontalAlignment = SwingConstants.CENTER
        jtfWeather.horizontalAlignment = SwingConstants.CENTER

        jrbFetchByCity.doClick()
    }

    private fun initListeners() {
        jmiAbout.addActionListener {
            JDAbout().also {
                it.setLocationRelativeTo(this)
                it.modalityType = Dialog.ModalityType.APPLICATION_MODAL
                it.isVisible = true
            }
        }

        jcbCountry.addItemListener {
            if (it.stateChange != ItemEvent.SELECTED) return@addItemListener

            lastCountryCode = ""
            clearInformation()

            val country = it.item

            if (country !is Country) {
                JOptionPane.showMessageDialog(null, "Could not retrieve cities list")
                return@addItemListener
            }

            citiesModel.removeAllElements()
            country.cities.forEach { city -> citiesModel.addElement(city) }
        }

        jcbCity.addItemListener {
            if (it.stateChange != ItemEvent.SELECTED) return@addItemListener

            clearInformation()

            val city = it.item

            if (city !is City) {
                JOptionPane.showMessageDialog(null, "Could not retrieve city coordinates")
                return@addItemListener
            }

            jtfAdmin.text = city.admin

            updateCoordinates(city)
        }

        jrbFetchByCity.addActionListener {
            lastCountryCode = ""
            clearInformation()

            jtfLatitude.isEnabled = false
            jtfLongitude.isEnabled = false
            jcbCity.isEnabled = true
            jcbCountry.isEnabled = true

            loadCountries()
            updateCoordinates()

            jcbCountry.grabFocus()
        }

        jrbFetchByCoordinates.addActionListener {
            lastCountryCode = ""
            clearInformation()

            jtfLatitude.text = ""
            jtfLongitude.text = ""

            jtfLatitude.isEnabled = true
            jtfLongitude.isEnabled = true
            jcbCity.isEnabled = false
            jcbCountry.isEnabled = false

            countriesModel.removeAllElements()
            citiesModel.removeAllElements()
            jtfAdmin.text = ""

            jtfLatitude.grabFocus()
        }

        jbFetch.addActionListener {
            if (working) return@addActionListener
            val state = validateFields() ?: return@addActionListener

            working = true
            jbFetch.isEnabled = false
            jpbFetch.isIndeterminate = true
            clearInformation()

            val handler = CoroutineExceptionHandler { _, exception ->
                working = false
                jbFetch.isEnabled = true
                jpbFetch.isIndeterminate = false

                JOptionPane.showMessageDialog(
                    null,
                    "${exception.message}"
                )
            }

            CoroutineScope(Dispatchers.IO).launch(handler) {
                if (state.fetchByCity) fetchByCity(state) else fetchByCoordinates(state)

                launch(Dispatchers.Main) {
                    working = false
                    jbFetch.isEnabled = true
                    jpbFetch.isIndeterminate = false
                }
            }
        }
    }

    private fun createFetchByPanel(): JPanel {
        val jpFetchBy = JPanel().also {
            it.border = TitledBorder("Fetch Method")
        }

        val gl = GroupLayout(jpFetchBy)
        jpFetchBy.layout = gl

        gl.autoCreateGaps = true
        gl.autoCreateContainerGaps = true

        gl.setHorizontalGroup(
            gl.createParallelGroup()
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlFetchBy)
                        .addComponent(jrbFetchByCity)
                        .addComponent(jrbFetchByCoordinates)
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlCountry)
                        .addComponent(
                            jcbCountry,
                            GroupLayout.DEFAULT_SIZE,
                            200,
                            Short.MAX_VALUE.toInt()
                        )
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlCity)
                        .addComponent(
                            jcbCity,
                            GroupLayout.DEFAULT_SIZE,
                            200,
                            Short.MAX_VALUE.toInt()
                        )
                        .addComponent(
                            jtfAdmin,
                            GroupLayout.DEFAULT_SIZE,
                            150,
                            Short.MAX_VALUE.toInt()
                        )
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlLatitude)
                        .addComponent(jtfLatitude, 100, 100, 100)
                        .addComponent(jlLongitude)
                        .addComponent(jtfLongitude, 100, 100, 100)
                )
        )

        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlFetchBy)
                        .addComponent(jrbFetchByCity)
                        .addComponent(jrbFetchByCoordinates)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlCountry)
                        .addComponent(jcbCountry, 25, 25, 25)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlCity)
                        .addComponent(jcbCity, 25, 25, 25)
                        .addComponent(jtfAdmin, 25, 25, 25)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlLatitude)
                        .addComponent(jtfLatitude, 25, 25, 25)
                        .addComponent(jlLongitude)
                        .addComponent(jtfLongitude, 25, 25, 25)
                )
        )

        return jpFetchBy
    }

    private fun createWeatherPanel(): JPanel {
        val panel = JPanel()
        val gl = GroupLayout(panel)

        panel.layout = gl
        gl.autoCreateGaps = true
        gl.autoCreateContainerGaps = true

        val jpFetchBy = createFetchByPanel()

        gl.setHorizontalGroup(
            gl.createParallelGroup()
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jLTime)
                        .addComponent(
                            jtfTime,
                            200,
                            200,
                            200
                        ).addGap(80)
                )
                .addGroup(
                    GroupLayout.Alignment.TRAILING,
                    gl.createSequentialGroup()
                        .addComponent(jlFlagImage, 70, 70, 70)
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlLocation)
                        .addComponent(
                            jtfLocation,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE,
                            Short.MAX_VALUE.toInt()
                        )
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jlWeather)
                        .addComponent(
                            jtfWeather,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE,
                            Short.MAX_VALUE.toInt()
                        )
                )
                .addGroup(
                    GroupLayout.Alignment.CENTER,
                    gl.createSequentialGroup()
                        .addComponent(jlCoordinates)
                        .addComponent(
                            jtfCoordinates,
                            GroupLayout.DEFAULT_SIZE,
                            220,
                            Short.MAX_VALUE.toInt()
                        )
                        .addComponent(jlTemperature)
                        .addComponent(
                            jtfTemperature,
                            GroupLayout.DEFAULT_SIZE,
                            170,
                            Short.MAX_VALUE.toInt()
                        )
                )
                .addComponent(jpFetchBy)
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jbFetch)
                )
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(
                            jpbFetch,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE,
                            Short.MAX_VALUE.toInt()
                        )
                )
        )

        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(
                    gl.createParallelGroup(GroupLayout.Alignment.TRAILING)
                        .addComponent(jLTime)
                        .addComponent(jtfTime, 25, 25, 25)
                        .addComponent(jlFlagImage, 70, 70, 70)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlLocation)
                        .addComponent(jtfLocation, 25, 25, 25)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlWeather)
                        .addComponent(jtfWeather, 25, 25, 25)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlCoordinates)
                        .addComponent(jtfCoordinates, 25, 25, 25)
                        .addComponent(jlTemperature)
                        .addComponent(jtfTemperature, 25, 25, 25)
                )
                .addComponent(jpFetchBy)
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jlTemperature)
                        .addComponent(jtfTemperature, 25, 25, 25)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jbFetch)
                )
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jpbFetch, 25, 25, 25)
                )
        )

        return panel
    }

    private fun createLayout() {
        val gl = GroupLayout(contentPane)
        contentPane.layout = gl

        gl.autoCreateGaps = true
        gl.autoCreateContainerGaps = true

        gl.setHorizontalGroup(
            gl.createParallelGroup()
                .addGroup(
                    gl.createSequentialGroup()
                        .addComponent(jtpWeather)
                )
        )

        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(
                    gl.createParallelGroup()
                        .addComponent(jtpWeather)
                )
        )
    }

    private fun clearInformation() {
        jtfTime.text = ""
        jtfLocation.text = ""
        jtfWeather.text = ""
        jtfCoordinates.text = ""
        jtfTemperature.text = ""
        if (lastCountryCode.isBlank()) jlFlagImage.icon = null
    }

    private fun validateFields(): State? {
        val latitude = jtfLatitude.text.toDoubleOrNull()
        val longitude = jtfLongitude.text.toDoubleOrNull()

        if (latitude == null) {
            JOptionPane.showMessageDialog(null, "No valid latitude")
            return null
        } else if (longitude == null) {
            JOptionPane.showMessageDialog(null, "No valid longitude")
            return null
        }

        val country = jcbCountry.selectedItem as? Country
        val city = jcbCity.selectedItem as? City

        return State(
            country = country,
            city = city,
            latitude = latitude,
            longitude = longitude,
            fetchByCity = jrbFetchByCity.isSelected
        )
    }

    private fun loadCountries() {
        if (countries.isEmpty()) {
            when (val result = LocationsDatabase.getAllCountries()) {
                is Right -> {
                    jpTemperatures.setCountries(result.value.toList())
                    countries.addAll(result.value)
                }

                is Left -> {
                    jrbFetchByCoordinates.doClick()
                    jrbFetchByCity.isEnabled = false
                    jtpWeather.setEnabledAt(1, false)
                    jpTemperatures.deactivateInterface(false)

                    JOptionPane.showMessageDialog(
                        null,
                        "Could not get coordinates database. The program will work only with coordinate fetch method"
                    )

                    return
                }
            }
        }

        countriesModel.addAll(countries)
        jcbCountry.selectedIndex = 0
    }

    private fun updateCoordinates(city: Any? = jcbCity.selectedItem) {
        if (city == null || city !is City) return
        jtfLatitude.text = city.latitude.toString()
        jtfLongitude.text = city.longitude.toString()
    }

    private suspend fun retrieveWeather(state: State): Boolean {
        val result = weather.update(
            state.latitude, state.longitude
        )

        when (result) {
            is Right -> {}
            is Left -> {
                JOptionPane.showMessageDialog(
                    this,
                    result.error.message
                )

                return false
            }
        }

        withContext(Dispatchers.Main) {
            jtfTemperature.text =
                "${weather.getTemperature()} °C / ${weather.getTemperature(TemperatureUnit.FAHRENHEIT)} °F"
            jtfWeather.text = weather.getWeatherDescription()
            jtfCoordinates.text = "LAT: ${state.latitude} - LONG: ${state.longitude}"
        }

        return true
    }

    private suspend fun retrieveTime(state: State) {
        val current = when (val result = getCurrentTime(state.latitude, state.longitude)) {
            is Right -> result.value

            is Left -> {
                JOptionPane.showMessageDialog(
                    null,
                    "Could not present time. ${result.error.message}"
                )

                return
            }
        }

        val localDateTime = try {
            LocalDateTime.of(
                current.year,
                current.month,
                current.day,
                current.hour,
                current.minute,
                current.seconds
            )
        } catch (ex: Throwable) {
            JOptionPane.showMessageDialog(
                null,
                "Could not present time: invalid format"
            )

            return
        }

        withContext(Dispatchers.Main) { jtfTime.text = localDateTime.toString() }
    }

    private suspend fun retrieveCountryFlag(countryCode: String) {
        if (lastCountryCode == countryCode) return

        val flagImgBytes = when (val result = getCountryFlagImg(countryCode)) {
            is Right -> {
                result.value
            }

            is Left -> {
                JOptionPane.showMessageDialog(
                    null,
                    "Could not retrieve country flag image. ${result.error.message}"
                )

                lastCountryCode = ""
                jlFlagImage.icon = null

                return
            }
        }

        try {
            val image = flagImgBytes.inputStream().use { ImageIO.read(it) }

            if (image == null) {
                JOptionPane.showMessageDialog(
                    null,
                    "Could not show country flag image. Failed to build image"
                )

                return
            }

            withContext(Dispatchers.Main) {
                jlFlagImage.icon = ImageIcon(image)
                lastCountryCode = countryCode
            }
        } catch (ex: IOException) {
            JOptionPane.showMessageDialog(
                null,
                "Could not show country flag image. Failed to build image"
            )
        }
    }

    private suspend fun fetchByCity(state: State) {
        if (!retrieveWeather(state)) return

        withContext(Dispatchers.Main) {
            jtfLocation.text = if (state.country != null && state.city != null)
                "${state.city}, ${state.country}" else ""
        }

        retrieveTime(state)

        if (state.country == null) {
            JOptionPane.showMessageDialog(
                null,
                "Could not retrieve image flag: no country code"
            )

            return
        }

        retrieveCountryFlag(state.country.iso2)
    }

    private suspend fun fetchByCoordinates(state: State) {
        if (!retrieveWeather(state)) return

        val result = getCoordinateInformation(
            state.latitude, state.longitude
        )

        val coordinatesInformation = when (result) {
            is Right -> result.value
            is Left -> {
                JOptionPane.showMessageDialog(
                    null,
                    "${result.error.message}"
                )
                return
            }
        }

        withContext(Dispatchers.Main) {
            jtfLocation.text = buildAddress(coordinatesInformation)
            println(coordinatesInformation.displayName)
        }

        retrieveTime(state)
        retrieveCountryFlag(coordinatesInformation.coordinateAddress.countryCode.uppercase())
    }

    fun createAndShow() {
        createLayout()
        pack()
        isResizable = false
        setLocationRelativeTo(null)
        defaultCloseOperation = EXIT_ON_CLOSE
        title = "Weather"
        isVisible = true
    }

    private data class State(
        val country: Country?,
        val city: City?,
        val latitude: Double,
        val longitude: Double,
        val fetchByCity: Boolean
    )
}

fun main() {
    EventQueue.invokeLater { AppGUI().createAndShow() }
}