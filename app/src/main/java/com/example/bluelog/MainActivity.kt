package com.example.bluelog

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluelog.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = BlueLogDatabase.getDatabase(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1565C0),
                    secondary = Color(0xFF0288D1),
                    background = Color(0xFFF4F7FB),
                    surface = Color.White
                )
            ) {
                BlueLogApp(db)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueLogApp(db: BlueLogDatabase) {
    val pockets by db.getAllPockets().collectAsState(initial = emptyList())
    val records by db.getAllRecords().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddEntryDialog by remember { mutableStateOf(false) }
    var showAddPocketDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Recent Activity, 1 = Lend & Borrow

    val totalCashPaise = pockets.filter { it.type == PocketType.CASH }.sumOf { it.balancePaise }
    val totalOnlinePaise = pockets.filter { it.type == PocketType.ONLINE }.sumOf { it.balancePaise }
    val toTakeBackPaise = records.filter { it.type == RecordType.TO_TAKE_BACK && !it.isSettled }
        .sumOf { it.amountPaise - it.settledPaise }
    val toGiveBackPaise = records.filter { it.type == RecordType.TO_GIVE_BACK && !it.isSettled }
        .sumOf { it.amountPaise - it.settledPaise }
    val netWorthPaise = (totalCashPaise + totalOnlinePaise) + toTakeBackPaise - toGiveBackPaise

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFF1565C0)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("BlueLog", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F7FB))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddEntryDialog = true },
                containerColor = Color(0xFF1565C0),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("Add Entry", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F7FB))
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // 1. Blue Balance & Net Worth Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0D47A1), Color(0xFF1976D2))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text("True Net Worth", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            Text(
                                MoneyMath.formatRupees(netWorthPaise),
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Cash in Hand", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    Text(
                                        MoneyMath.formatRupees(totalCashPaise),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }
                                Column {
                                    Text("Online Money", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    Text(
                                        MoneyMath.formatRupees(totalOnlinePaise),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "To Take Back: ${MoneyMath.formatRupees(toTakeBackPaise)}",
                                    color = Color(0xFFA5D6A7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "To Give Back: ${MoneyMath.formatRupees(toGiveBackPaise)}",
                                    color = Color(0xFFFFCC80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 2. Pockets Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Pockets & Accounts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = { showAddPocketDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Place")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pockets) { pocket ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp).widthIn(min = 120.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (pocket.type == PocketType.CASH) Icons.Default.Payments else Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = if (pocket.type == PocketType.CASH) Color(0xFF2E7D32) else Color(0xFF1565C0),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(pocket.name, fontSize = 13.sp, color = Color.DarkGray)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    MoneyMath.formatRupees(pocket.balancePaise),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. Filter Tabs
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Recent Activity") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Lend & Borrow") }
                    )
                }
            }

            // 4. Concise List
            val displayedRecords = if (selectedTab == 0) {
                records
            } else {
                records.filter { it.type == RecordType.TO_TAKE_BACK || it.type == RecordType.TO_GIVE_BACK }
            }

            if (displayedRecords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No entries yet. Tap '+ Add Entry' below!", color = Color.Gray)
                    }
                }
            } else {
                items(displayedRecords, key = { it.id }) { record ->
                    val pocketName = pockets.find { it.id == record.pocketId }?.name ?: "Pocket"
                    RecordItemCard(
                        record = record,
                        pocketName = pocketName,
                        pockets = pockets,
                        onEditDueDate = {
                            val cal = Calendar.getInstance()
                            record.dueDate?.let { cal.timeInMillis = it }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    cal.set(y, m, d)
                                    scope.launch { db.updateDueDate(record.id, cal.timeInMillis) }
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        onSettleAmount = { settlePaise, targetPocket ->
                            scope.launch {
                                db.settleLoanRecord(record, settlePaise, targetPocket)
                            }
                        },
                        onDeleteRecord = {
                            scope.launch {
                                db.deleteRecordAndRevertBalance(record)
                            }
                        }
                    )
                }
            }
        }

        if (showAddEntryDialog && pockets.isNotEmpty()) {
            AddEntryDialog(
                pockets = pockets,
                onDismiss = { showAddEntryDialog = false },
                onSaveSingle = { record, sourcePocket, targetPocket ->
                    scope.launch {
                        db.saveRecordAndAdjustBalance(record, sourcePocket, targetPocket)
                        showAddEntryDialog = false
                    }
                },
                onSaveFareReturn = { receivedPaise, spentPaise, pocket, title, note ->
                    scope.launch {
                        db.saveRecordAndAdjustBalance(
                            MoneyRecord(
                                title = "$title (Received)",
                                detailNote = note,
                                amountPaise = receivedPaise,
                                type = RecordType.RECEIVED,
                                pocketId = pocket.id
                            ),
                            pocket
                        )
                        val updatedPocket = pocket.copy(balancePaise = pocket.balancePaise + receivedPaise)
                        db.saveRecordAndAdjustBalance(
                            MoneyRecord(
                                title = "$title (Spent)",
                                detailNote = "Saved ${MoneyMath.formatRupees(receivedPaise - spentPaise)} back into ${pocket.name}",
                                amountPaise = spentPaise,
                                type = RecordType.SPENT,
                                pocketId = pocket.id
                            ),
                            updatedPocket
                        )
                        showAddEntryDialog = false
                    }
                }
            )
        }

        if (showAddPocketDialog) {
            AddPocketDialog(
                onDismiss = { showAddPocketDialog = false },
                onSave = { name, type, initialPaise ->
                    scope.launch {
                        db.insertPocket(Pocket(name = name, type = type, balancePaise = initialPaise))
                        showAddPocketDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun RecordItemCard(
    record: MoneyRecord,
    pocketName: String,
    pockets: List<Pocket>,
    onEditDueDate: () -> Unit,
    onSettleAmount: (Long, Pocket) -> Unit,
    onDeleteRecord: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val (amountColor, prefix) = when (record.type) {
        RecordType.SPENT -> Color(0xFFD32F2F) to "-"
        RecordType.RECEIVED -> Color(0xFF2E7D32) to "+"
        RecordType.MOVED -> Color(0xFF1565C0) to "⇄ "
        RecordType.TO_TAKE_BACK -> Color(0xFF00796B) to "Lent "
        RecordType.TO_GIVE_BACK -> Color(0xFFEF6C00) to "Borrowed "
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val dueFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!record.personName.isNullOrBlank()) "${record.personName} • ${record.title}" else record.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = buildString {
                            append(pocketName)
                            if (!record.affectsBalance) append(" • Past Entry")
                            if (record.isSettled) append(" • Settled ✓")
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    text = "$prefix${MoneyMath.formatRupees(record.amountPaise)}",
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(8.dp))

                    if (record.detailNote.isNotBlank()) {
                        Text("Detail: ${record.detailNote}", fontSize = 13.sp, color = Color.DarkGray)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "Logged on: ${dateFormatter.format(Date(record.createdAt))}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    if (record.type == RecordType.TO_TAKE_BACK || record.type == RecordType.TO_GIVE_BACK) {
                        val remaining = record.amountPaise - record.settledPaise
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Remaining: ${MoneyMath.formatRupees(remaining)} (Settled: ${MoneyMath.formatRupees(record.settledPaise)})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = onEditDueDate) {
                                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = record.dueDate?.let { "Due: ${dueFormatter.format(Date(it))}" } ?: "Set Due Date",
                                    fontSize = 12.sp
                                )
                            }
                            if (!record.isSettled && remaining > 0) {
                                Button(onClick = { showSettleDialog = true }) {
                                    Text("Return / Settle", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Delete Entry Button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete Entry", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Entry?") },
            text = {
                Text(
                    if (record.affectsBalance)
                        "This will remove '${record.title}' and automatically adjust your $pocketName balance back."
                    else
                        "This will remove past entry '${record.title}' without changing your $pocketName balance."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecord()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSettleDialog) {
        val remainingPaise = record.amountPaise - record.settledPaise
        var settleInput by remember { mutableStateOf((remainingPaise / 100.0).toString().removeSuffix(".0")) }
        var selectedPocket by remember { mutableStateOf(pockets.first()) }

        AlertDialog(
            onDismissRequest = { showSettleDialog = false },
            title = { Text("Record Return / Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settleInput,
                        onValueChange = { settleInput = it },
                        label = { Text("Amount (₹) - supports math e.g. 500/2") },
                        singleLine = true
                    )
                    Text("Select Pocket:", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pockets) { p ->
                            FilterChip(
                                selected = selectedPocket.id == p.id,
                                onClick = { selectedPocket = p },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val paise = MoneyMath.evaluateExpressionToPaise(settleInput)
                    if (paise != null && paise > 0) {
                        onSettleAmount(paise, selectedPocket)
                        showSettleDialog = false
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showSettleDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AddEntryDialog(
    pockets: List<Pocket>,
    onDismiss: () -> Unit,
    onSaveSingle: (MoneyRecord, Pocket, Pocket?) -> Unit,
    onSaveFareReturn: (Long, Long, Pocket, String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(RecordType.SPENT) }
    var isFareReturnMode by remember { mutableStateOf(false) }
    var isPastOpeningLoan by remember { mutableStateOf(false) }

    var amountText by remember { mutableStateOf("") }
    var spentReturnText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var detailNoteText by remember { mutableStateOf("") }
    var personNameText by remember { mutableStateOf("") }
    var selectedPocket by remember { mutableStateOf(pockets.first()) }
    var targetPocket by remember { mutableStateOf(pockets.last()) }
    var dueDateMillis by remember { mutableStateOf<Long?>(null) }

    val calculatedPaise = MoneyMath.evaluateExpressionToPaise(amountText)
    val dueFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Money Entry", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFareReturnMode, onCheckedChange = {
                        isFareReturnMode = it
                        if (it) isPastOpeningLoan = false
                    })
                    Text("Got money & kept change (e.g. ₹100 given, ₹80 spent)", fontSize = 12.sp)
                }

                if (!isFareReturnMode) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val types = listOf(
                            RecordType.SPENT to "Spent (-)",
                            RecordType.RECEIVED to "Received (+)",
                            RecordType.TO_TAKE_BACK to "Lent (Take Back)",
                            RecordType.TO_GIVE_BACK to "Borrowed (Give Back)",
                            RecordType.MOVED to "Move Money"
                        )
                        items(types) { (type, label) ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    if (type != RecordType.TO_TAKE_BACK && type != RecordType.TO_GIVE_BACK) {
                                        isPastOpeningLoan = false
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹) e.g. 20 or 20+15") },
                        singleLine = true,
                        supportingText = {
                            if (calculatedPaise != null && amountText.any { it in "+-*/×÷" }) {
                                Text("Calculated: ${MoneyMath.formatRupees(calculatedPaise)}", color = Color(0xFF1565C0))
                            }
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Money Given to You (e.g. 100)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = spentReturnText,
                        onValueChange = { spentReturnText = it },
                        label = { Text("Actual Spent (e.g. 80)") },
                        singleLine = true
                    )
                }

                if (!isFareReturnMode && (selectedType == RecordType.TO_TAKE_BACK || selectedType == RecordType.TO_GIVE_BACK)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPastOpeningLoan, onCheckedChange = { isPastOpeningLoan = it })
                        Text("Already happened earlier (Don't change current pocket balance)", fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = personNameText,
                        onValueChange = { personNameText = it },
                        label = { Text("Friend / Person Name") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    cal.set(y, m, d)
                                    dueDateMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dueDateMillis?.let { "Due Date: ${dueFormatter.format(Date(it))}" } ?: "Pick Due Date (Optional)")
                    }
                }

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Short Title (e.g. Samosa, Auto Fare)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = detailNoteText,
                    onValueChange = { detailNoteText = it },
                    label = { Text("Detailed Note (Hidden until tapped)") },
                    singleLine = true
                )

                Text(if (selectedType == RecordType.MOVED) "From Pocket:" else "Select Pocket:", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pockets) { pocket ->
                        FilterChip(
                            selected = selectedPocket.id == pocket.id,
                            onClick = { selectedPocket = pocket },
                            label = { Text(pocket.name, fontSize = 12.sp) }
                        )
                    }
                }

                if (!isFareReturnMode && selectedType == RecordType.MOVED) {
                    Text("To Pocket:", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pockets) { pocket ->
                            FilterChip(
                                selected = targetPocket.id == pocket.id,
                                onClick = { targetPocket = pocket },
                                label = { Text(pocket.name, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val title = titleText.ifBlank { "Entry" }
                if (isFareReturnMode) {
                    val recPaise = MoneyMath.evaluateExpressionToPaise(amountText)
                    val spPaise = MoneyMath.evaluateExpressionToPaise(spentReturnText)
                    if (recPaise != null && spPaise != null && recPaise >= spPaise) {
                        onSaveFareReturn(recPaise, spPaise, selectedPocket, title, detailNoteText)
                    }
                } else if (calculatedPaise != null && calculatedPaise > 0) {
                    onSaveSingle(
                        MoneyRecord(
                            title = title,
                            detailNote = detailNoteText,
                            amountPaise = calculatedPaise,
                            type = selectedType,
                            pocketId = selectedPocket.id,
                            toPocketId = if (selectedType == RecordType.MOVED) targetPocket.id else null,
                            personName = personNameText.ifBlank { null },
                            dueDate = dueDateMillis,
                            affectsBalance = !isPastOpeningLoan
                        ),
                        selectedPocket,
                        if (selectedType == RecordType.MOVED) targetPocket else null
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddPocketDialog(
    onDismiss: () -> Unit,
    onSave: (String, PocketType, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PocketType.CASH) }
    var initialAmount by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Cash Place / Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Under the Bed, PhonePe)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == PocketType.CASH,
                        onClick = { type = PocketType.CASH },
                        label = { Text("Physical Cash") }
                    )
                    FilterChip(
                        selected = type == PocketType.ONLINE,
                        onClick = { type = PocketType.ONLINE },
                        label = { Text("Online / Bank") }
                    )
                }
                OutlinedTextField(
                    value = initialAmount,
                    onValueChange = { initialAmount = it },
                    label = { Text("Starting Balance (₹)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = MoneyMath.evaluateExpressionToPaise(initialAmount) ?: 0L
                if (name.isNotBlank()) {
                    onSave(name.trim(), type, paise)
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}