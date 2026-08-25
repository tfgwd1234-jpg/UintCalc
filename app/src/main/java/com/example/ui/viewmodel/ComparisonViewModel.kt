package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ComparisonItem
import com.example.data.entity.ComparisonSession
import com.example.data.repository.ComparisonRepository
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ComparisonViewModel(private val repository: ComparisonRepository, private val application: android.app.Application) : ViewModel() {

    private val sharedPrefs = application.getSharedPreferences("unit_calc_settings", android.content.Context.MODE_PRIVATE)

    val volumeSmall = MutableStateFlow(sharedPrefs.getString("volume_small", "ml") ?: "ml")
    val volumeLarge = MutableStateFlow(sharedPrefs.getString("volume_large", "L") ?: "L")
    val weightLarge = MutableStateFlow(sharedPrefs.getString("weight_large", "kg") ?: "kg")
    val weightSmall = MutableStateFlow(sharedPrefs.getString("weight_small", "g") ?: "g")

    val hasSeenTutorial = MutableStateFlow(sharedPrefs.getBoolean("has_seen_tutorial", false))
    val forceUpdateRequired = MutableStateFlow(false)

    fun setHasSeenTutorial(seen: Boolean) {
        sharedPrefs.edit().putBoolean("has_seen_tutorial", seen).apply()
        hasSeenTutorial.value = seen
    }

    fun setVolumeSmall(unit: String) {
        sharedPrefs.edit().putString("volume_small", unit).apply()
        volumeSmall.value = unit
        updateActiveSessionUnits()
    }

    fun setVolumeLarge(unit: String) {
        sharedPrefs.edit().putString("volume_large", unit).apply()
        volumeLarge.value = unit
        updateActiveSessionUnits()
    }

    fun setWeightLarge(unit: String) {
        sharedPrefs.edit().putString("weight_large", unit).apply()
        weightLarge.value = unit
        updateActiveSessionUnits()
    }

    fun setWeightSmall(unit: String) {
        sharedPrefs.edit().putString("weight_small", unit).apply()
        weightSmall.value = unit
        updateActiveSessionUnits()
    }

    private fun updateActiveSessionUnits() {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            val updatedRefUnit = when (current.category) {
                "WEIGHT" -> {
                    if (current.referenceUnit.lowercase() in listOf("g", "oz")) {
                        weightSmall.value
                    } else {
                        weightLarge.value
                    }
                }
                "VOLUME" -> {
                    if (current.referenceUnit.lowercase() in listOf("ml", "fl.oz.", "fl. oz.", "fl oz")) {
                        volumeSmall.value
                    } else {
                        volumeLarge.value
                    }
                }
                else -> current.referenceUnit
            }

            val defaultItemUnit = when (current.category) {
                "WEIGHT" -> {
                    if (updatedRefUnit.lowercase() in listOf("kg", "lb")) {
                        weightLarge.value
                    } else {
                        weightSmall.value
                    }
                }
                "VOLUME" -> {
                    if (updatedRefUnit.lowercase() in listOf("l", "gal")) {
                        volumeLarge.value
                    } else {
                        volumeSmall.value
                    }
                }
                else -> current.referenceUnit
            }

            repository.updateSession(
                current.copy(referenceUnit = updatedRefUnit)
            )

            val items = activeItems.value
            items.forEach { item ->
                val oldUnit = item.unit
                if (oldUnit.lowercase() != defaultItemUnit.lowercase()) {
                    val oldMultiplier = com.example.util.UnitCalculator.getMultiplier(oldUnit)
                    val newMultiplier = com.example.util.UnitCalculator.getMultiplier(defaultItemUnit)
                    val newCapacity = if (newMultiplier > 0) {
                        (item.capacity * oldMultiplier) / newMultiplier
                    } else {
                        item.capacity
                    }
                    val roundedCapacity = Math.round(newCapacity * 1000.0) / 1000.0
                    repository.updateItem(item.copy(capacity = roundedCapacity, unit = defaultItemUnit))
                }
            }
        }
    }

    // All available comparison sessions
    val allSessions: StateFlow<List<ComparisonSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently active session ID
    private val _activeSessionId = MutableStateFlow<Int?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()

    // Active session details
    val activeSession: StateFlow<ComparisonSession?> = _activeSessionId
        .flatMapLatest { id ->
            if (id != null) {
                repository.allSessions.map { list -> list.find { it.id == id } }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Items belonging to the active session
    val activeItems: StateFlow<List<ComparisonItem>> = _activeSessionId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getItemsForSession(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkForForceUpdate()
        // Initialize with default session if none exists
        viewModelScope.launch {
            allSessions.collect { sessions ->
                if (sessions.isEmpty()) {
                    createAndSelectDefaultSession()
                } else {
                    if (_activeSessionId.value == null) {
                        _activeSessionId.value = sessions.first().id
                    }
                    val currentId = _activeSessionId.value
                    if (currentId != null) {
                        val session = sessions.find { it.id == currentId }
                        if (session != null) {
                            ensureMinimumItems(session.id, session.referenceUnit)
                        }
                    }
                }
            }
        }
    }

    private fun checkForForceUpdate() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 1 //Remote Config 시간 설정(3600초=1시간)
            }
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(mapOf("min_version" to 1L))
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                try {
                    val minVersion = remoteConfig.getLong("min_version")
                    val currentVersionCode = try {
                        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pInfo.versionCode.toLong()
                        }
                    } catch (e: Exception) {
                        com.example.BuildConfig.VERSION_CODE.toLong()
                    }

                    if (currentVersionCode < minVersion) {
                        forceUpdateRequired.value = true
                    }
                } catch (e: Exception) {
                    // Safe fallback
                }
            }
        } catch (e: Exception) {
            // Safe fallback if Firebase uninitialized
        }
    }

    private suspend fun ensureMinimumItems(sessionId: Int, defaultUnit: String) {
        val currentItems = repository.getItemsForSession(sessionId).first()
        val productAPrefix = application.getString(com.example.R.string.product_a)
        val productBPrefix = application.getString(com.example.R.string.product_b)
        val defaultNames = listOf(productAPrefix, productBPrefix)

        for (i in currentItems.size until 2) {
            val name = if (i < defaultNames.size) defaultNames[i] else application.getString(com.example.R.string.product_default, ('A'.code + i).toChar().toString())
            repository.insertItem(
                ComparisonItem(
                    sessionId = sessionId,
                    name = name,
                    price = 0.0,
                    capacity = 0.0,
                    unit = defaultUnit,
                    bundleCount = 1
                )
            )
        }
    }

    private suspend fun createAndSelectDefaultSession() {
        val defaultTitle = application.getString(com.example.R.string.smart_unit_title)
        val productAPrefix = application.getString(com.example.R.string.product_a)
        val productBPrefix = application.getString(com.example.R.string.product_b)
        val defaultSession = ComparisonSession(
            title = defaultTitle,
            category = "WEIGHT",
            referenceValue = 100.0,
            referenceUnit = weightSmall.value,
            isSaved = false
        )
        val sessionId = repository.insertSession(defaultSession).toInt()

        // Add 2 default items (Product A & Product B)
        repository.insertItem(
            ComparisonItem(
                sessionId = sessionId,
                name = productAPrefix,
                price = 0.0,
                capacity = 0.0,
                unit = weightSmall.value,
                bundleCount = 1
            )
        )
        repository.insertItem(
            ComparisonItem(
                sessionId = sessionId,
                name = productBPrefix,
                price = 0.0,
                capacity = 0.0,
                unit = weightSmall.value,
                bundleCount = 1
            )
        )
        _activeSessionId.value = sessionId
    }

    // Create a new session
    fun createNewSession(title: String, category: String = "WEIGHT") {
        viewModelScope.launch {
            val defaultTitle = application.getString(com.example.R.string.smart_unit_title)
            val productAPrefix = application.getString(com.example.R.string.product_a)
            val productBPrefix = application.getString(com.example.R.string.product_b)
            val defaultUnitUnit = when (category) {
                "WEIGHT" -> weightSmall.value
                "VOLUME" -> volumeSmall.value
                else -> application.getString(com.example.R.string.pcs)
            }
            val (refVal, refUnit) = when (category) {
                "WEIGHT" -> Pair(100.0, weightSmall.value)
                "VOLUME" -> Pair(100.0, volumeSmall.value)
                else -> Pair(1.0, defaultUnitUnit)
            }
            val newSession = ComparisonSession(
                title = title.ifBlank { defaultTitle },
                category = category,
                referenceValue = refVal,
                referenceUnit = refUnit,
                isSaved = false
            )
            val sessionId = repository.insertSession(newSession).toInt()

            // Add 2 default items matching category
            repository.insertItem(
                ComparisonItem(
                    sessionId = sessionId,
                    name = productAPrefix,
                    price = 0.0,
                    capacity = 0.0,
                    unit = defaultUnitUnit,
                    bundleCount = 1
                )
            )
            repository.insertItem(
                ComparisonItem(
                    sessionId = sessionId,
                    name = productBPrefix,
                    price = 0.0,
                    capacity = 0.0,
                    unit = defaultUnitUnit,
                    bundleCount = 1
                )
            )
            _activeSessionId.value = sessionId
        }
    }

    // Save active session with custom title and start a new unsaved session automatically
    fun saveActiveSessionAndCreateNew(title: String) {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            val defaultTitle = application.getString(com.example.R.string.smart_unit_title)
            // 1. Save the current session
            repository.updateSession(
                current.copy(
                    title = title.ifBlank { defaultTitle },
                    isSaved = true,
                    timestamp = System.currentTimeMillis()
                )
            )

            // 2. Start a new unsaved session automatically
            val productAPrefix = application.getString(com.example.R.string.product_a)
            val productBPrefix = application.getString(com.example.R.string.product_b)
            val defaultUnitUnit = when (current.category) {
                "WEIGHT" -> weightSmall.value
                "VOLUME" -> volumeSmall.value
                else -> application.getString(com.example.R.string.pcs)
            }
            val (refVal, refUnit) = when (current.category) {
                "WEIGHT" -> Pair(100.0, weightSmall.value)
                "VOLUME" -> Pair(100.0, volumeSmall.value)
                else -> Pair(1.0, defaultUnitUnit)
            }
            val newSession = ComparisonSession(
                title = defaultTitle,
                category = current.category,
                referenceValue = refVal,
                referenceUnit = refUnit,
                isSaved = false
            )
            val sessionId = repository.insertSession(newSession).toInt()

            // Add 2 default items
            repository.insertItem(
                ComparisonItem(
                    sessionId = sessionId,
                    name = productAPrefix,
                    price = 0.0,
                    capacity = 0.0,
                    unit = defaultUnitUnit,
                    bundleCount = 1
                )
            )
            repository.insertItem(
                ComparisonItem(
                    sessionId = sessionId,
                    name = productBPrefix,
                    price = 0.0,
                    capacity = 0.0,
                    unit = defaultUnitUnit,
                    bundleCount = 1
                )
            )
            _activeSessionId.value = sessionId
        }
    }

    // Update the display order of the saved sessions by updating their timestamps
    fun updateSessionsOrder(sessions: List<ComparisonSession>) {
        viewModelScope.launch {
            val baseTime = System.currentTimeMillis()
            sessions.forEachIndexed { index, session ->
                // Ensure strictly decreasing timestamps to respect timestamp DESC ordering
                repository.updateSession(session.copy(timestamp = baseTime - (index * 1000L)))
            }
        }
    }

    // Select an existing session
    fun selectSession(sessionId: Int) {
        _activeSessionId.value = sessionId
        viewModelScope.launch {
            val session = repository.allSessions.first().find { it.id == sessionId }
            if (session != null) {
                ensureMinimumItems(session.id, session.referenceUnit)
            }
        }
    }

    // Rename active session
    fun renameActiveSession(newTitle: String) {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            repository.updateSession(current.copy(title = newTitle))
        }
    }

    // Update active session category
    fun changeActiveSessionCategory(category: String) {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            val (refVal, refUnit) = when (category) {
                "WEIGHT" -> Pair(100.0, weightSmall.value)
                "VOLUME" -> Pair(100.0, volumeSmall.value)
                else -> Pair(1.0, "개")
            }
            repository.updateSession(
                current.copy(
                    category = category,
                    referenceValue = refVal,
                    referenceUnit = refUnit
                )
            )

            // Update all existing items to use default unit for this category
            val defaultUnit = when (category) {
                "WEIGHT" -> weightSmall.value
                "VOLUME" -> volumeSmall.value
                else -> "개"
            }
            val items = activeItems.value
            items.forEach { item ->
                repository.updateItem(item.copy(unit = defaultUnit))
            }
        }
    }

    // Update active session reference base (e.g. 100g, 10g, 1kg)
    fun changeActiveSessionReference(value: Double, unit: String) {
        val current = activeSession.value ?: return
        val category = current.category
        viewModelScope.launch {
            repository.updateSession(
                current.copy(
                    category = category,
                    referenceValue = value,
                    referenceUnit = unit
                )
            )

            // Update all active items to use the new reference unit, converting their capacity
            val items = activeItems.value
            items.forEach { item ->
                val oldUnit = item.unit
                if (oldUnit.lowercase() != unit.lowercase()) {
                    val oldMultiplier = com.example.util.UnitCalculator.getMultiplier(oldUnit)
                    val newMultiplier = com.example.util.UnitCalculator.getMultiplier(unit)
                    val newCapacity = if (newMultiplier > 0) {
                        (item.capacity * oldMultiplier) / newMultiplier
                    } else {
                        item.capacity
                    }
                    val roundedCapacity = Math.round(newCapacity * 1000.0) / 1000.0
                    repository.updateItem(item.copy(capacity = roundedCapacity, unit = unit))
                }
            }
        }
    }

    // Delete active session
    fun deleteActiveSession() {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            repository.deleteSession(current)
            _activeSessionId.value = null
        }
    }

    // Delete arbitrary session
    fun deleteSession(session: ComparisonSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_activeSessionId.value == session.id) {
                _activeSessionId.value = null
            }
        }
    }

    // Add a comparison item to the active session
    fun addItemToActiveSession() {
        val sessionId = _activeSessionId.value ?: return
        val session = activeSession.value ?: return
        val category = session.category
        val defaultUnit = session.referenceUnit
        viewModelScope.launch {
            val items = activeItems.value
            val nextLetter = if (items.size < 26) ('A'.code + items.size).toChar().toString() else (items.size + 1).toString()
            val defaultItemName = application.getString(com.example.R.string.product_default, nextLetter)
            repository.insertItem(
                ComparisonItem(
                    sessionId = sessionId,
                    name = defaultItemName,
                    price = 0.0,
                    capacity = 0.0,
                    unit = defaultUnit,
                    bundleCount = 1
                )
            )
        }
    }

    // Update an item's details
    fun updateItem(item: ComparisonItem) {
        viewModelScope.launch {
            repository.updateItem(item)
        }
    }

    // Delete an item
    fun deleteItem(item: ComparisonItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    // Reset the active session items to blank initial state (Product A & Product B)
    fun resetActiveSession() {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            val items = activeItems.value
            val productAPrefix = application.getString(com.example.R.string.product_a)
            val productBPrefix = application.getString(com.example.R.string.product_b)
            val defaultNames = listOf(productAPrefix, productBPrefix)

            items.forEachIndexed { index, item ->
                if (index < 2) {
                    val defaultName = if (index < defaultNames.size) defaultNames[index] else application.getString(com.example.R.string.product_default, ('A'.code + index).toChar().toString())
                    repository.updateItem(
                        item.copy(
                            name = defaultName,
                            price = 0.0,
                            capacity = 0.0,
                            unit = session.referenceUnit,
                            bundleCount = 1
                        )
                    )
                } else {
                    repository.deleteItem(item)
                }
            }
            if (items.size < 2) {
                ensureMinimumItems(session.id, session.referenceUnit)
            }
        }
    }
}

class ComparisonViewModelFactory(private val repository: ComparisonRepository, private val application: android.app.Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ComparisonViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ComparisonViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
