package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.example.R
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ComparisonItem
import com.example.data.entity.ComparisonSession
import com.example.ui.theme.*
import com.example.ui.viewmodel.ComparisonViewModel
import com.example.util.UnitCalculator
import kotlinx.coroutines.launch

enum class TabItem {
    HOME, HISTORY, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    viewModel: ComparisonViewModel,
    modifier: Modifier = Modifier
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val volSmall by viewModel.volumeSmall.collectAsStateWithLifecycle()
    val volLarge by viewModel.volumeLarge.collectAsStateWithLifecycle()
    val wtLarge by viewModel.weightLarge.collectAsStateWithLifecycle()
    val wtSmall by viewModel.weightSmall.collectAsStateWithLifecycle()
    val hasSeenTutorial by viewModel.hasSeenTutorial.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    var lastBackPressTime by remember { mutableStateOf(0L) }
    val exitMessage = stringResource(id = R.string.back_press_exit_msg)

    // Dialog & Screen States
    var selectedTab by remember { mutableStateOf(TabItem.HOME) }
    var showTutorialManual by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (selectedTab != TabItem.HOME) {
            selectedTab = TabItem.HOME
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? Activity)?.finishAffinity()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showSaveComparisonDialog by remember { mutableStateOf(false) }
    var sessionToDeleteConfirm by remember { mutableStateOf<ComparisonSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var saveComparisonTitle by remember { mutableStateOf("") }
    var newSessionTitle by remember { mutableStateOf("") }
    var newSessionCategory by remember { mutableStateOf("WEIGHT") }

    // Drag and drop states for History List
    val historyLazyListState = rememberLazyListState()
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var initiallyDraggedIndex by remember { mutableStateOf<Int?>(null) }
    var reorderedSessions by remember { mutableStateOf<List<ComparisonSession>?>(null) }

    // Keyboard mode: true = Custom Mart Keypad, false = Standard System Keyboard
    var useMartKeypad by remember { mutableStateOf(false) }

    // Active field focused for custom keypad
    // Triple(ItemId, FieldName, CurrentValueString)
    var activeField by remember { mutableStateOf<Triple<Int, String, String>?>(null) }

    // Compute comparison results
    val session = activeSession
    val comparisonResults = remember(activeItems, session) {
        if (session != null) {
            UnitCalculator.calculateResults(
                items = activeItems,
                referenceValue = session.referenceValue,
                referenceUnit = session.referenceUnit
            )
        } else {
            emptyList()
        }
    }

    val bestResult = comparisonResults.firstOrNull { it.isBest }

    // Keep inputs synced
    // To prevent cursor jumping and text formatting quirks, we maintain localized text state maps
    val itemPriceStates = remember { mutableStateMapOf<Int, String>() }
    val itemCapacityStates = remember { mutableStateMapOf<Int, String>() }
    val itemBundleStates = remember { mutableStateMapOf<Int, String>() }
    val itemNameStates = remember { mutableStateMapOf<Int, String>() }

    // Sync from DB updates only when not editing or when active items size changes
    LaunchedEffect(activeItems) {
        activeItems.forEach { item ->
            if (!itemPriceStates.containsKey(item.id)) {
                itemPriceStates[item.id] = if (item.price == 0.0) "" else item.price.toInt().toString()
            }
            if (!itemCapacityStates.containsKey(item.id)) {
                itemCapacityStates[item.id] = if (item.capacity == 0.0) "" else item.capacity.toString().removeSuffix(".0")
            }
            if (!itemBundleStates.containsKey(item.id)) {
                itemBundleStates[item.id] = item.bundleCount.toString()
            }
            if (!itemNameStates.containsKey(item.id)) {
                itemNameStates[item.id] = item.name
            }
        }
        // Clean up stale IDs
        val activeIds = activeItems.map { it.id }.toSet()
        itemPriceStates.keys.retainAll(activeIds)
        itemCapacityStates.keys.retainAll(activeIds)
        itemBundleStates.keys.retainAll(activeIds)
        itemNameStates.keys.retainAll(activeIds)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .height(56.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    when (selectedTab) {
                        TabItem.HOME -> {
                            val defaultSmartTitleEn = "Unit Calculator"
                            val defaultSmartTitleKo = "단위 계산기"
                            val defaultSmartTitleJa = "単位計算機"
                            val isTitleEditable = session != null &&
                                    session.title != defaultSmartTitleKo &&
                                    session.title != defaultSmartTitleJa &&
                                    session.title != defaultSmartTitleEn
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = if (isTitleEditable) {
                                    Modifier
                                        .clickable {
                                            renameText = if (session?.title == defaultSmartTitleKo || session?.title == defaultSmartTitleJa || session?.title == defaultSmartTitleEn) {
                                                ""
                                            } else {
                                                session?.title ?: ""
                                            }
                                            showRenameDialog = true
                                        }
                                        .testTag("session_title_container")
                                } else {
                                    Modifier.testTag("session_title_container")
                                }
                            ) {
                                // Bento signature icon container with the app icon image
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                
                                val displayTitle = if (session?.title == defaultSmartTitleKo || session?.title == defaultSmartTitleJa || session?.title == defaultSmartTitleEn) {
                                    stringResource(id = R.string.smart_unit_title)
                                } else {
                                    session?.title ?: stringResource(id = R.string.smart_unit_title)
                                }
                                Text(
                                    text = displayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        letterSpacing = (-0.5).sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isTitleEditable) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(id = R.string.edit_comparison_name_title),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                        TabItem.HISTORY -> {
                            Text(
                                text = stringResource(id = R.string.past_history_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        TabItem.SETTINGS -> {
                            Text(
                                text = stringResource(id = R.string.settings_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                },
                actions = {
                    when (selectedTab) {
                        TabItem.HOME -> {
                            IconButton(
                                onClick = {
                                    showTutorialManual = true
                                },
                                modifier = Modifier.testTag("help_tutorial_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(id = R.string.tutorial_show_guide),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    newSessionTitle = ""
                                    newSessionCategory = "WEIGHT"
                                    showNewSessionDialog = true
                                },
                                modifier = Modifier.testTag("new_comparison_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(id = R.string.new_comparison),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        else -> {
                            // No action buttons on History and Settings screens
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding()
            ) {
                // Dynamic Custom Mart Keypad Panel
                AnimatedVisibility(
                    visible = selectedTab == TabItem.HOME && useMartKeypad && activeField != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    activeField?.let { fieldInfo ->
                        MartKeypadPanel(
                            fieldInfo = fieldInfo,
                            onValueChange = { newVal ->
                                val (itemId, fieldName, _) = fieldInfo
                                activeField = Triple(itemId, fieldName, newVal)

                                val item = activeItems.find { it.id == itemId }
                                if (item != null) {
                                    when (fieldName) {
                                        "price" -> {
                                            itemPriceStates[itemId] = newVal
                                            viewModel.updateItem(item.copy(price = newVal.toDoubleOrNull() ?: 0.0))
                                        }
                                        "capacity" -> {
                                            itemCapacityStates[itemId] = newVal
                                            viewModel.updateItem(item.copy(capacity = newVal.toDoubleOrNull() ?: 0.0))
                                        }
                                        "bundle" -> {
                                            itemBundleStates[itemId] = newVal
                                            viewModel.updateItem(item.copy(bundleCount = newVal.toIntOrNull() ?: 1))
                                        }
                                    }
                                }
                            },
                            onClose = {
                                activeField = null
                                focusManager.clearFocus()
                            },
                            onAction = {
                                val (itemId, fieldName, _) = fieldInfo
                                if (fieldName == "price") {
                                    val currentVal = itemCapacityStates[itemId] ?: ""
                                    activeField = Triple(itemId, "capacity", currentVal)
                                } else if (fieldName == "capacity") {
                                    val currentVal = itemBundleStates[itemId] ?: ""
                                    activeField = Triple(itemId, "bundle", currentVal)
                                } else {
                                    activeField = null
                                    focusManager.clearFocus()
                                }
                            },
                            sessionCategory = session?.category ?: "WEIGHT"
                        )
                    }
                }

                // Bottom Navigation Bar - only show if the custom keypad is NOT visible
                val showNavBar = !(selectedTab == TabItem.HOME && useMartKeypad && activeField != null)
                AnimatedVisibility(
                    visible = showNavBar,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        com.example.ui.components.AdmobBannerView()
                        NavigationBar(
                            modifier = Modifier.height(64.dp).testTag("bottom_nav_bar"),
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == TabItem.HOME,
                                onClick = { selectedTab = TabItem.HOME },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == TabItem.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = stringResource(id = R.string.tab_home)
                                    )
                                },
                                label = { Text(stringResource(id = R.string.tab_home)) },
                                modifier = Modifier.testTag("tab_home")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TabItem.HISTORY,
                                onClick = { selectedTab = TabItem.HISTORY },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == TabItem.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                                        contentDescription = stringResource(id = R.string.tab_history)
                                    )
                                },
                                label = { Text(stringResource(id = R.string.tab_history)) },
                                modifier = Modifier.testTag("tab_history")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TabItem.SETTINGS,
                                onClick = { selectedTab = TabItem.SETTINGS },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == TabItem.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = stringResource(id = R.string.tab_settings)
                                    )
                                },
                                label = { Text(stringResource(id = R.string.tab_settings)) },
                                modifier = Modifier.testTag("tab_settings")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                TabItem.HOME -> {
                    if (session != null) {
                        // 3-way Category Selector on the Home Screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "WEIGHT" to stringResource(id = R.string.category_weight),
                                "VOLUME" to stringResource(id = R.string.category_volume),
                                "COUNT" to stringResource(id = R.string.category_count)
                            ).forEach { (cat, label) ->
                                val isSelected = session.category == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.changeActiveSessionCategory(cat)
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("home_category_tab_$cat"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Reference Base Bar (e.g. 100g, 1kg, 10ml, 1L, etc.)
                        ReferenceBaseBar(
                            currentRefValue = session.referenceValue,
                            currentRefUnit = session.referenceUnit,
                            sessionCategory = session.category,
                            volumeSmall = volSmall,
                            volumeLarge = volLarge,
                            weightSmall = wtSmall,
                            weightLarge = wtLarge,
                            onReferenceChanged = { valAmt, unitStr ->
                                viewModel.changeActiveSessionReference(valAmt, unitStr)
                            }
                        )

                        // List of comparison cards
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item {
                                AnimatedVisibility(
                                    visible = bestResult != null && bestResult.referencePrice > 0,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    bestResult?.let { best ->
                                        BestWinnerCard(
                                            result = best,
                                            refValue = session.referenceValue,
                                            refUnit = session.referenceUnit
                                        )
                                    }
                                }
                            }
                            items(comparisonResults, key = { it.item.id }) { result ->
                                val itemId = result.item.id
                                ComparisonItemCard(
                                    result = result,
                                    priceText = itemPriceStates[itemId] ?: "",
                                    onPriceChanged = { text ->
                                        itemPriceStates[itemId] = text
                                        viewModel.updateItem(result.item.copy(price = text.toDoubleOrNull() ?: 0.0))
                                    },
                                    capacityText = itemCapacityStates[itemId] ?: "",
                                    onCapacityChanged = { text ->
                                        itemCapacityStates[itemId] = text
                                        viewModel.updateItem(result.item.copy(capacity = text.toDoubleOrNull() ?: 0.0))
                                    },
                                    bundleText = itemBundleStates[itemId] ?: "1",
                                    onBundleChanged = { text ->
                                        itemBundleStates[itemId] = text
                                        viewModel.updateItem(result.item.copy(bundleCount = text.toIntOrNull() ?: 1))
                                    },
                                    nameText = itemNameStates[itemId] ?: "",
                                    onNameChanged = { text ->
                                        itemNameStates[itemId] = text
                                        viewModel.updateItem(result.item.copy(name = text))
                                    },
                                    onUnitChanged = { unitStr ->
                                        viewModel.updateItem(result.item.copy(unit = unitStr))
                                    },
                                    onDelete = {
                                        viewModel.deleteItem(result.item)
                                    },
                                    useMartKeypad = useMartKeypad,
                                    isActiveField = { fieldName ->
                                        activeField?.first == itemId && activeField?.second == fieldName
                                    },
                                    onRequestFocus = { fieldName, currentVal ->
                                        if (useMartKeypad) {
                                            keyboardController?.hide()
                                            activeField = Triple(itemId, fieldName, currentVal)
                                        }
                                    },
                                    sessionCategory = session.category,
                                    refValue = session.referenceValue,
                                    refUnit = session.referenceUnit,
                                    volumeSmall = volSmall,
                                    volumeLarge = volLarge,
                                    weightSmall = wtSmall,
                                    weightLarge = wtLarge
                                )
                            }

                            item {
                                Button(
                                    onClick = {
                                        viewModel.addItemToActiveSession()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("add_item_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(id = R.string.add_product),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.add_product), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            item {
                                val defaultSmartTitleEn = "Smart Unit Calculator"
                                val defaultSmartTitleKo = "스마트 단위 비교기"
                                val defaultSmartTitleJa = "スマート単位比較機"
                                val localizedSmartTitle = stringResource(id = R.string.smart_unit_title)
                                Button(
                                    onClick = {
                                        saveComparisonTitle = ""
                                        showSaveComparisonDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("save_comparison_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(id = R.string.save_comparison),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.save_comparison), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Empty Session State
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                TabItem.HISTORY -> {
                    val savedSessions = allSessions.filter { it.isSaved }
                    if (savedSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.empty_history),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val currentSessionsList = reorderedSessions ?: savedSessions
                        LazyColumn(
                            state = historyLazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                        ) {
                            itemsIndexed(
                                items = currentSessionsList,
                                key = { _, item -> item.id }
                            ) { index, sessionItem ->
                                val isActive = sessionItem.id == activeSessionId
                                val isDragged = draggedIndex == index

                                val currentListState = rememberUpdatedState(currentSessionsList)
                                val currentIndexState = rememberUpdatedState(index)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragged) 10f else 1f)
                                        .graphicsLayer {
                                            translationY = if (isDragged) dragOffset else 0f
                                            scaleX = if (isDragged) 1.05f else 1f
                                            scaleY = if (isDragged) 1.05f else 1f
                                            alpha = if (isDragged) 0.9f else 1f
                                        }
                                        .pointerInput(sessionItem.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { _ ->
                                                    reorderedSessions = currentListState.value.toList()
                                                    draggedIndex = currentIndexState.value
                                                    initiallyDraggedIndex = currentIndexState.value
                                                    dragOffset = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount.y

                                                    val currentDraggedIndex = draggedIndex
                                                    val list = reorderedSessions
                                                    val draggedItem = list?.getOrNull(currentDraggedIndex ?: -1)
                                                    if (draggedItem != null && currentDraggedIndex != null) {
                                                        val lazyLayoutInfo = historyLazyListState.layoutInfo
                                                        val draggedItemInfo = lazyLayoutInfo.visibleItemsInfo.find { it.key == draggedItem.id }
                                                        if (draggedItemInfo != null) {
                                                            val draggedItemCenter = draggedItemInfo.offset + draggedItemInfo.size / 2 + dragOffset

                                                            val swapWithItem = lazyLayoutInfo.visibleItemsInfo.find { itemInfo ->
                                                                itemInfo.key != draggedItem.id &&
                                                                draggedItemCenter > itemInfo.offset &&
                                                                draggedItemCenter < itemInfo.offset + itemInfo.size
                                                            }

                                                            if (swapWithItem != null) {
                                                                val targetIndex = list.indexOfFirst { it.id == swapWithItem.key }
                                                                if (targetIndex != -1) {
                                                                    val newList = list.toMutableList()
                                                                    val temp = newList[currentDraggedIndex]
                                                                    newList[currentDraggedIndex] = newList[targetIndex]
                                                                    newList[targetIndex] = temp

                                                                    val currentItemOffset = draggedItemInfo.offset
                                                                    val targetItemOffset = swapWithItem.offset
                                                                    dragOffset += (currentItemOffset - targetItemOffset)

                                                                    draggedIndex = targetIndex
                                                                    reorderedSessions = newList
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    val finalSessions = reorderedSessions
                                                    if (finalSessions != null) {
                                                        viewModel.updateSessionsOrder(finalSessions)
                                                    }
                                                    draggedIndex = null
                                                    initiallyDraggedIndex = null
                                                    dragOffset = 0f
                                                    reorderedSessions = null
                                                },
                                                onDragCancel = {
                                                    draggedIndex = null
                                                    initiallyDraggedIndex = null
                                                    dragOffset = 0f
                                                    reorderedSessions = null
                                                }
                                            )
                                        }
                                        .clickable {
                                            if (draggedIndex == null) {
                                                viewModel.selectSession(sessionItem.id)
                                                selectedTab = TabItem.HOME
                                            }
                                        }
                                        .testTag("history_session_${sessionItem.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = sessionItem.title,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                val localizedUnit = when (sessionItem.referenceUnit) {
                                                    "개", "pcs", "個" -> stringResource(id = R.string.pcs)
                                                    else -> sessionItem.referenceUnit
                                                }
                                                val referenceText = "${sessionItem.referenceValue.toString().removeSuffix(".0")}$localizedUnit"
                                                Text(
                                                    text = stringResource(id = R.string.basis, referenceText),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                
                                                // Display the saved date and time!
                                                val formattedTime = remember(sessionItem.timestamp) {
                                                    val language = java.util.Locale.getDefault().language
                                                    val pattern = when (language) {
                                                        "ko" -> "yyyy년 M월 d일 HH:mm"
                                                        "ja" -> "yyyy年M月d日 HH:mm"
                                                        else -> "yyyy-MM-dd HH:mm"
                                                    }
                                                    java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(sessionItem.timestamp))
                                                }
                                                Text(
                                                    text = stringResource(id = R.string.save_time, formattedTime),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        // Delete session icon
                                        IconButton(
                                            onClick = { sessionToDeleteConfirm = sessionItem },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.delete_comparison),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TabItem.SETTINGS -> {
                    val context = LocalContext.current
                    val appName = context.getString(R.string.app_name)
                    
                    val volSmall by viewModel.volumeSmall.collectAsState()
                    val volLarge by viewModel.volumeLarge.collectAsState()
                    val wtLarge by viewModel.weightLarge.collectAsState()
                    val wtSmall by viewModel.weightSmall.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.unit_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 1. Volume (Small)
                        UnitSettingsCard(
                            title = stringResource(id = R.string.volume_small_label),
                            options = listOf("ml", "fl.oz."),
                            selectedOption = volSmall,
                            onOptionSelected = { viewModel.setVolumeSmall(it) }
                        )

                        // 2. Volume (Large)
                        UnitSettingsCard(
                            title = stringResource(id = R.string.volume_large_label),
                            options = listOf("L", "gal"),
                            selectedOption = volLarge,
                            onOptionSelected = { viewModel.setVolumeLarge(it) }
                        )

                        // 3. Weight (Small)
                        UnitSettingsCard(
                            title = stringResource(id = R.string.weight_small_label),
                            options = listOf("g", "oz"),
                            selectedOption = wtSmall,
                            onOptionSelected = { viewModel.setWeightSmall(it) }
                        )

                        // 4. Weight (Large)
                        UnitSettingsCard(
                            title = stringResource(id = R.string.weight_large_label),
                            options = listOf("kg", "lb"),
                            selectedOption = wtLarge,
                            onOptionSelected = { viewModel.setWeightLarge(it) }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Help & Usage Guide Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTutorialManual = true
                                }
                                .testTag("settings_show_tutorial"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.tutorial_show_guide),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // In-App Review Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val activity = context as? Activity
                                    activity?.let {
                                        val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(it)
                                        val request = reviewManager.requestReviewFlow()
                                        request.addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val reviewInfo = task.result
                                                reviewManager.launchReviewFlow(it, reviewInfo)
                                            }
                                        }
                                    }
                                }
                                .testTag("settings_in_app_review"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.in_app_review),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Privacy Policy Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/unitcalc-privacy"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // safe fallback
                                    }
                                }
                                .testTag("settings_privacy_policy"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.privacy_policy),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Feedback & Suggestions Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val subject = context.getString(R.string.email_subject)
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:")
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf("jjoooo.co@gmail.com"))
                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(emailIntent, "Send email..."))
                                    } catch (e: Exception) {
                                        // safe fallback
                                    }
                                }
                                .testTag("settings_feedback"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.contact_suggestions),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Active Session Dialog
    if (showRenameDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(showRenameDialog) {
            focusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(id = R.string.edit_comparison_name_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(id = R.string.item_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameActiveSession(renameText)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("rename_confirm_button")
                ) {
                    Text(stringResource(id = R.string.change))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Create New Session Dialog
    if (showNewSessionDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(showNewSessionDialog) {
            focusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text(stringResource(id = R.string.new_comparison_start)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newSessionTitle,
                        onValueChange = { newSessionTitle = it },
                        label = { Text(stringResource(id = R.string.comparison_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("new_session_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSessionTitle.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.validation_empty_title), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.createNewSession(newSessionTitle)
                            showNewSessionDialog = false
                        }
                    },
                    modifier = Modifier.testTag("new_session_confirm_button")
                ) {
                    Text(stringResource(id = R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Save Comparison Dialog
    if (showSaveComparisonDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(showSaveComparisonDialog) {
            focusRequester.requestFocus()
        }
        val currentDateTimeStr = remember(showSaveComparisonDialog) {
            val language = java.util.Locale.getDefault().language
            val pattern = when (language) {
                "ko" -> "yyyy년 M월 d일 HH:mm"
                "ja" -> "yyyy年M月d日 HH:mm"
                else -> "yyyy-MM-dd HH:mm"
            }
            java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date())
        }
        AlertDialog(
            onDismissRequest = { showSaveComparisonDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.save_comparison_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(id = R.string.save_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.save_time, currentDateTimeStr),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = saveComparisonTitle,
                        onValueChange = { saveComparisonTitle = it },
                        label = { Text(stringResource(id = R.string.comparison_name)) },
                        placeholder = { Text(stringResource(id = R.string.comparison_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("save_comparison_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveComparisonTitle.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.validation_empty_title), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.saveActiveSessionAndCreateNew(saveComparisonTitle)
                            showSaveComparisonDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_comparison_confirm_button")
                ) {
                    Text(stringResource(id = R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveComparisonDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (sessionToDeleteConfirm != null) {
        val targetSession = sessionToDeleteConfirm!!
        val defaultSmartTitleEn = "Smart Unit Calculator"
        val defaultSmartTitleKo = "스마트 단위 비교기"
        val defaultSmartTitleJa = "スマート単位比較機"
        val displayTitle = if (targetSession.title == defaultSmartTitleKo || targetSession.title == defaultSmartTitleJa || targetSession.title == defaultSmartTitleEn) {
            stringResource(id = R.string.smart_unit_title)
        } else {
            targetSession.title
        }
        AlertDialog(
            onDismissRequest = { sessionToDeleteConfirm = null },
            title = {
                Text(stringResource(id = R.string.delete_confirm_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = stringResource(id = R.string.delete_confirm_msg, displayTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(targetSession)
                        sessionToDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("delete_session_confirm_button")
                ) {
                    Text(stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDeleteConfirm = null }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Tutorial Onboarding & Manual Dialog
    if (!hasSeenTutorial) {
        TutorialDialog(
            onDismiss = {
                viewModel.setHasSeenTutorial(true)
            }
        )
    } else if (showTutorialManual) {
        TutorialDialog(
            onDismiss = {
                showTutorialManual = false
            }
        )
    }

}

// ==========================================
// Composable Sub-Components
// ==========================================

@Composable
fun ReferenceBaseBar(
    currentRefValue: Double,
    currentRefUnit: String,
    sessionCategory: String,
    volumeSmall: String = "ml",
    volumeLarge: String = "L",
    weightSmall: String = "g",
    weightLarge: String = "kg",
    onReferenceChanged: (Double, String) -> Unit
) {
    val language = java.util.Locale.getDefault().language
    val formatLabel: (Double, String) -> String = { valAmt, unitStr ->
        val formattedValue = valAmt.toString().removeSuffix(".0")
        val localizedUnit = when (unitStr) {
            "개" -> when (language) {
                "ko" -> "개"
                "ja" -> "個"
                else -> "pc"
            }
            else -> unitStr
        }
        when (language) {
            "ko" -> "${formattedValue}${localizedUnit}당"
            "ja" -> "${formattedValue}${localizedUnit}当たり"
            else -> "per ${formattedValue}${localizedUnit}"
        }
    }

    val options = when (sessionCategory) {
        "WEIGHT" -> listOf(
            Triple(10.0, weightSmall, formatLabel(10.0, weightSmall)),
            Triple(100.0, weightSmall, formatLabel(100.0, weightSmall)),
            Triple(1.0, weightLarge, formatLabel(1.0, weightLarge))
        )
        "VOLUME" -> listOf(
            Triple(10.0, volumeSmall, formatLabel(10.0, volumeSmall)),
            Triple(100.0, volumeSmall, formatLabel(100.0, volumeSmall)),
            Triple(1.0, volumeLarge, formatLabel(1.0, volumeLarge))
        )
        else -> listOf(
            Triple(1.0, "개", formatLabel(1.0, "개")),
            Triple(10.0, "개", formatLabel(10.0, "개")),
            Triple(100.0, "개", formatLabel(100.0, "개"))
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(id = R.string.comparison_standard_qty),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (valAmt, unitStr, label) ->
                val isSelected = currentRefValue == valAmt && (
                    currentRefUnit.lowercase() == unitStr.lowercase() ||
                    (currentRefUnit.lowercase() in listOf("개", "pcs", "個") && unitStr.lowercase() in listOf("개", "pcs", "個")) ||
                    (currentRefUnit.lowercase() in listOf("l", "L") && unitStr.lowercase() in listOf("l", "L"))
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onReferenceChanged(valAmt, unitStr) }
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("ref_chip_${valAmt}_$unitStr"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BestWinnerCard(
    result: UnitCalculator.ComparisonResult,
    refValue: Double,
    refUnit: String
) {
    val titleText = result.item.name.ifBlank { stringResource(id = R.string.best_deal_title) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(width = 1.dp, color = PastelBestBorder, shape = RoundedCornerShape(28.dp))
            .testTag("best_winner_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = PastelBestBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Top Row: Best Choice tag and item label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(PastelBestText, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BEST CHOICE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp
                        )
                    )
                }
                
                Text(
                    text = titleText,
                    color = PastelBestText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Middle Area: Huge Display Price
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                val currencySymbol = stringResource(id = R.string.won_symbol)
                Text(
                    text = "$currencySymbol ${UnitCalculator.formatPrice(result.referencePrice)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = PastelBestTextDark,
                        letterSpacing = (-1).sp
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                val localizedUnit = when (refUnit) {
                    "개", "pcs", "個" -> stringResource(id = R.string.pcs)
                    else -> refUnit
                }
                Text(
                    text = "/ ${refValue.toString().removeSuffix(".0")}$localizedUnit",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PastelBestTextDark.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Bottom Area: Info / Saving difference
            val savingText = if (result.percentageDifferenceText.isNotEmpty() && result.percentageDifferenceText != "0%") {
                stringResource(id = R.string.cheaper_by_desc, result.percentageDifferenceText)
            } else {
                stringResource(id = R.string.best_deal_desc)
            }
            
            Text(
                text = savingText,
                style = MaterialTheme.typography.bodyMedium,
                color = PastelBestText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun KeyboardModeRow(
    useMartKeypad: Boolean,
    onToggle: (Boolean) -> Unit,
    onAddItem: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Custom Keypad / System Soft Keyboard
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggle(!useMartKeypad) }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (useMartKeypad) Icons.Default.Keyboard else Icons.Default.Apps,
                contentDescription = stringResource(id = R.string.input_mode_toggle),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (useMartKeypad) stringResource(id = R.string.keyboard_mode) else stringResource(id = R.string.mart_keypad),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        // Add Product Button (matches tests and Bento CTA styling)
        Button(
            onClick = onAddItem,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_product), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(id = R.string.add_product), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComparisonItemCard(
    result: UnitCalculator.ComparisonResult,
    priceText: String,
    onPriceChanged: (String) -> Unit,
    capacityText: String,
    onCapacityChanged: (String) -> Unit,
    bundleText: String,
    onBundleChanged: (String) -> Unit,
    nameText: String,
    onNameChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onDelete: () -> Unit,
    useMartKeypad: Boolean,
    isActiveField: (String) -> Boolean,
    onRequestFocus: (String, String) -> Unit,
    sessionCategory: String,
    refValue: Double,
    refUnit: String,
    volumeSmall: String = "ml",
    volumeLarge: String = "L",
    weightSmall: String = "g",
    weightLarge: String = "kg"
) {
    val isBest = result.isBest && result.isValid
    val focusManager = LocalFocusManager.current
    val borderColor = if (isBest) PastelBestBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val borderThickness = if (isBest) 1.5.dp else 1.dp
    val backgroundBrush = if (isBest) {
        Brush.verticalGradient(listOf(PastelBestBg, PastelBestBg))
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = borderThickness, color = borderColor, shape = RoundedCornerShape(16.dp))
            .testTag("item_card_${result.item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Top line: Name and Delete button & Best Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isBest) {
                            Box(
                                modifier = Modifier
                                    .background(PastelBestText, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("best_tag")
                            ) {
                                  Text(
                                    "BEST",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        BasicTextField(
                            value = nameText,
                            onValueChange = onNameChanged,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isBest) PastelBestText else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("name_field_${result.item.id}"),
                            decorationBox = { innerTextField ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (nameText.isEmpty()) {
                                            Text(
                                                stringResource(id = R.string.item_name_placeholder),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isBest) PastelBestText.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(id = R.string.edit_possible),
                                        tint = if (isBest) PastelBestText.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("delete_item_button_${result.item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = if (isBest) PastelBestText.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Grid Inputs: Price, Capacity, Packs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Price Field
                    InputGridCell(
                        label = stringResource(id = R.string.price_won),
                        valueText = priceText,
                        onValueChange = onPriceChanged,
                        placeholder = "0",
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("price_cell_${result.item.id}"),
                        useCustomKeypad = useMartKeypad,
                        isFocused = isActiveField("price"),
                        onClick = { onRequestFocus("price", priceText) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        visualTransformation = ThousandsCommaTransformation()
                    )

                    // Capacity Field
                    val capacityLabel = when (sessionCategory) {
                        "WEIGHT", "VOLUME" -> {
                            val prefix = stringResource(id = R.string.capacity_label)
                            "$prefix ($refUnit)"
                        }
                        else -> stringResource(id = R.string.bundle_capacity_label)
                    }
                    InputGridCell(
                        label = capacityLabel,
                        valueText = capacityText,
                        onValueChange = onCapacityChanged,
                        placeholder = "0",
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("capacity_cell_${result.item.id}"),
                        useCustomKeypad = useMartKeypad,
                        isFocused = isActiveField("capacity"),
                        onClick = { onRequestFocus("capacity", capacityText) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        visualTransformation = ThousandsCommaTransformation()
                    )

                    // Bundle/Pack Count Field
                    InputGridCell(
                        label = stringResource(id = R.string.pcs_bundle),
                        valueText = bundleText,
                        onValueChange = onBundleChanged,
                        placeholder = "1",
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("bundle_cell_${result.item.id}"),
                        useCustomKeypad = useMartKeypad,
                        isFocused = isActiveField("bundle"),
                        onClick = { onRequestFocus("bundle", bundleText) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }

                // Dynamic Unit Selection Row -> Now only shows Results!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Calculation Results display on the right
                    Column(horizontalAlignment = Alignment.End) {
                        if (result.isValid) {
                            val formattedPrice = UnitCalculator.formatPrice(result.referencePrice)
                            val displayPriceText = when (java.util.Locale.getDefault().language) {
                                "ko", "ja" -> "$formattedPrice${stringResource(id = R.string.price_unit)}"
                                else -> "${stringResource(id = R.string.won_symbol)}$formattedPrice"
                            }
                            Text(
                                text = displayPriceText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isBest) PastelBestText else MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val localizedRefUnit = when (refUnit) {
                                    "개", "pcs", "個" -> stringResource(id = R.string.pcs)
                                    else -> refUnit
                                }
                                Text(
                                    text = stringResource(id = R.string.per_unit_format, "${refValue.toString().removeSuffix(".0")}$localizedRefUnit"),
                                    fontSize = 10.sp,
                                    color = if (isBest) PastelBestText.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!isBest && result.priceDifferenceText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.expensive_desc, result.priceDifferenceText, result.percentageDifferenceText),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(id = R.string.price_placeholder),
                                fontSize = 12.sp,
                                color = if (isBest) PastelBestText.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InputGridCell(
    label: String,
    valueText: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    useCustomKeypad: Boolean = false,
    isFocused: Boolean = false,
    onClick: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(
                    if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    if (useCustomKeypad) {
                        onClick()
                    }
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (useCustomKeypad) {
                // Read-only text display when custom keypad is active
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val transformedText = remember(valueText, visualTransformation) {
                        visualTransformation.filter(AnnotatedString(valueText)).text.text
                    }
                    Text(
                        text = transformedText.ifBlank { placeholder },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (valueText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (isFocused) {
                        // Tiny pulsing cursor indicator
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            } else {
                // Native Text Field when standard keyboard mode is active
                BasicTextField(
                    value = valueText,
                    onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' }) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    decorationBox = { innerTextField ->
                        if (valueText.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

@Composable
fun MartKeypadPanel(
    fieldInfo: Triple<Int, String, String>,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    onAction: () -> Unit,
    sessionCategory: String
) {
    val (_, fieldName, currentValue) = fieldInfo

    val fieldNameLocalized = when (fieldName) {
        "price" -> stringResource(id = R.string.price_won)
        "capacity" -> if (sessionCategory == "COUNT") stringResource(id = R.string.bundle_capacity_label) else stringResource(id = R.string.capacity_label)
        else -> stringResource(id = R.string.bundle_count_label)
    }

    val actionKeyLabel = when (fieldName) {
        "price", "capacity" -> {
            val lang = java.util.Locale.getDefault().language
            when (lang) {
                "ko" -> "다음"
                "ja" -> "次へ"
                else -> "Next"
            }
        }
        else -> "✓"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mart_keypad"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Status of currently focused field
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.field_entering, fieldNameLocalized),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardHide, contentDescription = stringResource(id = R.string.keypad_close))
                }
            }

            // Keypad Grid: 4x4 or 3x4 layout
            val buttons = listOf(
                listOf("1", "2", "3", "⌫"),
                listOf("4", "5", "6", "C"),
                listOf("7", "8", "9", "+1"),
                listOf("00", "0", ".", actionKeyLabel)
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { char ->
                        val isSpecial = char == "⌫" || char == "C" || char == actionKeyLabel || char == "+1"
                        val containerColor = when (char) {
                            actionKeyLabel -> MaterialTheme.colorScheme.primary
                            "⌫", "C" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val contentColor = when (char) {
                            actionKeyLabel -> MaterialTheme.colorScheme.onPrimary
                            "⌫", "C" -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerColor)
                                .clickable {
                                    when (char) {
                                        "⌫" -> {
                                            if (currentValue.isNotEmpty()) {
                                                onValueChange(currentValue.dropLast(1))
                                            }
                                        }
                                        "C" -> {
                                            onValueChange("")
                                        }
                                        actionKeyLabel -> {
                                            onAction()
                                        }
                                        "+1" -> {
                                            val currentAmt = currentValue.toDoubleOrNull() ?: 0.0
                                            onValueChange((currentAmt + 1).toString().removeSuffix(".0"))
                                        }
                                        else -> {
                                            // Handle multiple decimal point protection
                                            if (char == "." && currentValue.contains(".")) return@clickable
                                            onValueChange(currentValue + char)
                                        }
                                    }
                                }
                                .testTag("numpad_key_$char"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdBannerArea() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("ad_banner"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "AD",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(id = R.string.premium_upgrade_ad),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(id = R.string.more),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

class ThousandsCommaTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val parts = originalText.split('.')
        val integerPart = parts[0]
        val fractionalPart = if (parts.size > 1) "." + parts[1] else ""

        val formattedInteger = StringBuilder()
        for (i in integerPart.indices) {
            formattedInteger.append(integerPart[i])
            val remainingDigits = integerPart.length - 1 - i
            if (remainingDigits > 0 && remainingDigits % 3 == 0) {
                formattedInteger.append(',')
            }
        }

        val transformed = formattedInteger.toString() + fractionalPart

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val safeOffset = offset.coerceAtMost(originalText.length)
                val decimalIndex = originalText.indexOf('.')
                val endOfIntegerOffset = if (decimalIndex != -1) decimalIndex else originalText.length
                
                val integerOffset = safeOffset.coerceAtMost(endOfIntegerOffset)
                var commasBefore = 0
                for (i in 0 until integerOffset) {
                    val remainingDigits = integerPart.length - 1 - i
                    if (remainingDigits > 0 && remainingDigits % 3 == 0) {
                        commasBefore++
                    }
                }
                return (safeOffset + commasBefore).coerceAtMost(transformed.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val safeOffset = offset.coerceAtMost(transformed.length)
                var commasBefore = 0
                for (i in 0 until safeOffset) {
                    if (transformed[i] == ',') {
                        commasBefore++
                    }
                }
                return (safeOffset - commasBefore).coerceAtMost(originalText.length)
            }
        }

        return TransformedText(AnnotatedString(transformed), offsetMapping)
    }
}

@Composable
fun UnitSettingsCard(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.lowercase() == selectedOption.lowercase()
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialDialog(
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 6

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("tutorial_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.tutorial_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Main content of current step
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            // Welcome
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.tutorial_welcome_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.tutorial_welcome_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                        1 -> {
                            // Home units
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.tutorial_home_units_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.tutorial_home_units_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                        2 -> {
                            // Best Choice
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.tutorial_home_best_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.tutorial_home_best_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                        3 -> {
                            // Actions
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.tutorial_home_actions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.tutorial_home_actions_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                        4 -> {
                            // History
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.tutorial_history_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.tutorial_history_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                        5 -> {
                            // Settings
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.tutorial_settings_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.tutorial_settings_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Navigation controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left button: Skip or Prev
                    if (currentStep > 0) {
                        TextButton(onClick = { currentStep-- }) {
                            Text(
                                text = stringResource(id = R.string.tutorial_btn_prev),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(id = R.string.tutorial_btn_skip),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Center Page Indicator (Dots)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(totalSteps) { idx ->
                            Box(
                                modifier = Modifier
                                    .size(if (idx == currentStep) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (idx == currentStep) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    // Right button: Next or Done
                    Button(
                        onClick = {
                            if (currentStep < totalSteps - 1) {
                                currentStep++
                            } else {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentStep == totalSteps - 1) {
                                stringResource(id = R.string.tutorial_btn_done)
                            } else {
                                stringResource(id = R.string.tutorial_btn_next)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}



