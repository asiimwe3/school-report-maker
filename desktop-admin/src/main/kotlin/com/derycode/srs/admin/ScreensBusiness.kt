package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.DocxReport
import java.text.SimpleDateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
// School Fees — structure per class/term, payments, balances
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FeesScreen(state: AppState) {
    val d = state.data
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    // v2.2.3: filter by level so schools with many streams (and Senior 4+) don't hit the old
    // hard cutoff — previously only the FIRST 10 classes ever showed here, silently hiding
    // Senior 4, 5 and 6 for any school with 7 primary classes + streams.
    var feesLevel by remember { mutableStateOf<Level?>(null) }
    var feeCategory by remember { mutableStateOf(FeeCategory.SCHOOL_FEES) }
    var feeAmount by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var payStudent by remember { mutableStateOf("") }
    var payAmount by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf("Mobile Money") }
    var payReceipt by remember { mutableStateOf("") }
    // v2.2.7 balance protection: admin PIN before fee amounts can be edited
    var pinGate by remember { mutableStateOf(false) }
    var gatePin by remember { mutableStateOf("") }
    var gateMsg by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }

    val cls = d.classes.firstOrNull { it.id == classId }
    val students = d.enrollments.filter { it.classId == classId && (year == null || it.academicYearId == year.id) }
        .mapNotNull { e -> d.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }
    val structure = d.feeStructures.firstOrNull { it.classId == classId && term != null && it.termId == term.id && it.category == feeCategory }
    val cur = d.settings.currencySymbol

    val saveStructure = {
        val amt = feeAmount.toDoubleOrNull() ?: structure?.amount
        if (classId.isNotBlank() && term != null && amt != null && amt > 0) {
            val id = structure?.id ?: state.repo.nextId()
            state.repo.mutate("FEE_STRUCTURE_SET", "FeeStructure", id, new = "$amt") { dd ->
                dd.copy(feeStructures = (dd.feeStructures.filter { !(it.classId == classId && it.termId == term.id && it.category == feeCategory) } +
                    FeeStructure(id = id, classId = classId, academicYearId = year?.id ?: "", termId = term.id, name = feeCategory.label, amount = amt, category = feeCategory)))
            }
            feeAmount = ""; msg = "${feeCategory.label} set for ${cls?.name}: ${cur} ${amt.toLong()}"
            state.refresh()
        } else msg = "Enter a valid amount."
    }

    ScreenTitle("Fee Collection", "School, boarding and bursary ledgers stay independent from one another.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Collection ledger", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeeCategory.entries.forEach { category ->
                    FilterChip(selected = feeCategory == category, onClick = { feeCategory = category }, label = { Text(category.label, fontSize = 13.sp) })
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Balances and payments are calculated only within the selected ledger.", color = Theme.MUTED, fontSize = 13.sp)
        }
        // ── Balance protection (v2.2.7): admin PIN required to edit fee amounts ──
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Balance protection (PIN)", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (d.settings.feePinHash.isNotBlank()) "ON ✓" else "OFF", color = if (d.settings.feePinHash.isNotBlank()) Theme.GOOD else Theme.MUTED, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("When ON, changing a class's expected fee amount (what every student owes) asks for this PIN first — balances can't be quietly edited.",
                color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (d.settings.feePinHash.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(newPin, { newPin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.width(150.dp), "New PIN (4–6 digits)")
                    TextField(confirmPin, { confirmPin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.width(140.dp), "Repeat PIN")
                    Btn("Protect balances") {
                        when {
                            newPin.length < 4 -> pinMsg = "PIN must be 4–6 digits."
                            newPin != confirmPin -> pinMsg = "PINs don't match."
                            else -> {
                                state.repo.mutate("FEE_PIN_SET", "AppSettings", "feePin", new = "***") { dd ->
                                    dd.copy(settings = dd.settings.copy(feePinHash = com.derycode.srs.core.support.LicenseKeys.hash("feepin|" + newPin)))
                                }
                                newPin = ""; confirmPin = ""; pinMsg = "Fee balance protection is ON ✓"
                                state.refresh()
                            }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("To change the PIN: remove it (current PIN needed), then set a new one.", color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextField(gatePin, { gatePin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.width(130.dp), "Current PIN")
                    Btn("Remove protection", primary = false) {
                        if (com.derycode.srs.core.support.LicenseKeys.hash("feepin|" + gatePin) == d.settings.feePinHash) {
                            state.repo.mutate("FEE_PIN_REMOVED", "AppSettings", "feePin") { dd ->
                                dd.copy(settings = dd.settings.copy(feePinHash = ""))
                            }
                            gatePin = ""; pinMsg = "Fee balance protection removed."
                            state.refresh()
                        } else pinMsg = "Wrong PIN."
                    }
                }
            }
            if (pinMsg.isNotBlank()) Text(pinMsg, color = if (pinMsg.contains("✓")) Theme.GOOD else Theme.WARN, fontSize = 13.sp)
        }
        if (pinGate) CardBox {
            Text("PIN required to change ${feeCategory.label} for ${cls?.name ?: "this class"}", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(gatePin, { gatePin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.width(130.dp), "PIN")
                Btn("Confirm") {
                    if (com.derycode.srs.core.support.LicenseKeys.hash("feepin|" + gatePin) == d.settings.feePinHash) {
                        pinGate = false; gatePin = ""; gateMsg = ""; saveStructure()
                    } else gateMsg = "Wrong PIN."
                }
                Btn("Cancel", primary = false) { pinGate = false; gatePin = ""; gateMsg = "" }
            }
            if (gateMsg.isNotBlank()) Text(gateMsg, color = Theme.WARN, fontSize = 13.sp)
        }
        // ── Fee structure ──
        CardBox {
            Text("${feeCategory.label} structure (${term?.let { "Term ${it.number} ${year?.year}" } ?: "no term"})", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (d.classes.isEmpty()) Text("Add classes first.", color = Theme.MUTED, fontSize = 14.sp)
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = feesLevel == null, onClick = { feesLevel = null }, label = { Text("All levels", fontSize = 12.sp) })
                    Level.entries.forEach { lvl ->
                        FilterChip(selected = feesLevel == lvl, onClick = { feesLevel = lvl }, label = { Text(levelLabel(lvl), fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                ClassPickerChips(d.classes.filter { it.active && (feesLevel == null || it.level == feesLevel) }, classId) { classId = it }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(if (structure != null && feeAmount.isBlank()) structure.amount.toLong().toString() else feeAmount,
                        { feeAmount = it }, Modifier.width(160.dp), "Amount per term")
                    Text(cur, color = Theme.MUTED, fontSize = 14.sp)
                    Btn(if (structure == null) "Set fee" else "Update fee") {
                        if (d.settings.feePinHash.isNotBlank()) { gatePin = ""; gateMsg = ""; pinGate = true } else saveStructure()
                    }
                    if (structure != null) Text("Current: ${cur} ${structure.amount.toLong()}", color = Theme.GOOD, fontSize = 14.sp)
                }
            }
        }

        // ── Record payment ──
        if (students.isNotEmpty()) {
            CardBox {
                Text("Record ${feeCategory.label.lowercase()}", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    students.forEach { s ->
                        FilterChip(selected = payStudent == s.id, onClick = { payStudent = s.id },
                            label = { Text(s.fullName.take(18), fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Amount ($cur)"); TextField(payAmount, { payAmount = it }, Modifier.fillMaxWidth(), "e.g. 150000") }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Method")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Mobile Money", "Cash", "Bank").forEach { m ->
                                FilterChip(selected = payMethod == m, onClick = { payMethod = m }, label = { Text(m, fontSize = 12.sp) })
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) { FieldLabel("Receipt no."); TextField(payReceipt, { payReceipt = it }, Modifier.fillMaxWidth(), "optional") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Save payment") {
                        val amt = payAmount.toDoubleOrNull()
                        if (payStudent.isBlank()) msg = "Select a student first."
                        else if (amt == null || amt <= 0) msg = "Enter a valid amount."
                        else {
                            val s = students.firstOrNull { it.id == payStudent }
                            state.repo.mutate("FEE_PAYMENT_RECORDED", "FeePayment", payStudent, new = "$amt") { dd ->
                                dd.copy(feePayments = dd.feePayments + FeePayment(
                                    id = state.repo.nextId(), studentId = payStudent,
                                    academicYearId = year?.id ?: "", termId = term?.id ?: "",
                                    amount = amt, date = System.currentTimeMillis(), method = payMethod, receipt = payReceipt, category = feeCategory))
                            }
                            payAmount = ""; payReceipt = ""
                            msg = "${feeCategory.label} recorded: ${cur} ${amt.toLong()} for ${s?.fullName}"
                            state.refresh()
                        }
                    }
                    if (msg.isNotBlank()) Text(msg, color = Theme.GOOD, fontSize = 14.sp)
                }
            }
        }

        // ── Balances ──
        CardBox {
            Text("${feeCategory.label} balances — ${cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "select a class"}",
                color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (structure == null) Text("No fee set for this class/term yet — set one above.", color = Theme.MUTED, fontSize = 14.sp)
            else {
                var totalPaid = 0.0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(2.2f)) { HeaderCell("Student") }
                    Box(Modifier.weight(1f)) { HeaderCell("Term fee") }
                    Box(Modifier.weight(1f)) { HeaderCell("Paid") }
                    Box(Modifier.weight(1f)) { HeaderCell("Balance") }
                    Box(Modifier.weight(0.8f)) { HeaderCell("Status") }
                }
                LazyColumn(Modifier.height(300.dp)) {
                    items(students) { s ->
                        val paid = d.feePayments.filter { it.studentId == s.id && it.termId == term?.id && it.category == feeCategory }.sumOf { it.amount }
                        totalPaid += paid
                        val bal = structure.amount - paid
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(2.2f)) { Cell(s.fullName, bold = true) }
                            Box(Modifier.weight(1f)) { Cell(structure.amount.toLong().toString(), color = Theme.MUTED) }
                            Box(Modifier.weight(1f)) { Cell(paid.toLong().toString(), color = Theme.GOOD) }
                            Box(Modifier.weight(1f)) { Cell(bal.toLong().toString(), color = if (bal <= 0) Theme.GOOD else Theme.WARN) }
                            Box(Modifier.weight(0.8f)) { Cell(if (bal <= 0) "Cleared" else "Due", color = if (bal <= 0) Theme.GOOD else Theme.WARN) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val expected = structure.amount * students.size
                val outstanding = students.sumOf { s -> (structure.amount - d.feePayments.filter { it.studentId == s.id && it.termId == term?.id && it.category == feeCategory }.sumOf { it.amount }).coerceAtLeast(0.0) }
                Text("Expected: $cur ${expected.toLong()}    Collected: $cur ${totalPaid.toLong()}    Outstanding: $cur ${outstanding.toLong()}", color = Theme.GOOD, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Btn("Print defaulter list (Word)", primary = false, onClick = {
                    val rows = students.mapNotNull { s ->
                        val paid = d.feePayments.filter { it.studentId == s.id && it.termId == term?.id && it.category == feeCategory }.sumOf { it.amount }
                        val bal = structure.amount - paid
                        if (bal > 0) listOf(s.fullName, structure.amount.toLong().toString(), paid.toLong().toString(), bal.toLong().toString()) else null
                    }
                    val label = cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "Class"
                    val f = dataDir.resolve("reports").resolve("defaulters-${feeCategory.name.lowercase()}-${label.replace(" ", "")}-term${term?.number}.docx")
                    DocxReport.writeDefaulterList(f, d, label, "${feeCategory.label} · Term ${term?.number} ${year?.year ?: ""}",
                        cur, rows, listOf(expected.toLong().toString(), totalPaid.toLong().toString(), outstanding.toLong().toString()))
                    try { java.awt.Desktop.getDesktop().open(f.toFile()) } catch (_: Exception) { }
                })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plans & Pricing — subscription tiers + activation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlansScreen(state: AppState) {
    val d = state.data
    var ref by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val plan = d.settings.licencePlan
    val cur = d.settings.currencySymbol

    val n = d.students.size
    val rec = when { n <= 100 -> "Starter"; n <= 300 -> "Standard"; n <= 800 -> "Growth"; n <= 2000 -> "School" ; else -> "Multi-Branch" }
    val plans = listOf(
        "STARTER" to listOf("Up to 100 students", "$cur 100,000 / year", "Everything in Trial", "Full marks & reports"),
        "STANDARD" to listOf("Up to 300 students", "$cur 180,000 / year", "Everything in Starter", "Priority WhatsApp support"),
        "GROWTH" to listOf("Up to 800 students", "$cur 300,000 / year", "Everything in Standard", "Free updates all year"),
        "SCHOOL" to listOf("Up to 2,000 students", "$cur 450,000 / year", "Everything in Growth", "Report branding"),
        "MULTI-BRANCH" to listOf("Unlimited students & branches", "$cur 600,000 / year", "Central templates & schemes", "Onboarding call"))

    ScreenTitle("Plans & Pricing", "Pick the tier that fits your school — pricing scales with student numbers.")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CardBox {
            Text("Your school: $n students → recommended plan: $rec", color = Theme.ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            plans.forEach { (name, feats) ->
                val pretty = name.lowercase().replaceFirstChar { it.uppercase() }.replace("-", " ")
                val isTrial = plan == "Trial"
                PlanCard(name, feats[1], feats[0], feats, plan.equals(pretty, true), Modifier.width(240.dp))
            }
        }
        CardBox {
            Text("Current plan: $plan", color = if (plan == "Trial") Theme.WARN else Theme.GOOD, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (d.settings.licenceRef.isNotBlank()) Text("Licence ref: ${d.settings.licenceRef}", color = Theme.MUTED, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text("How to pay", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("1. MTN MoMo / Airtel Money to 0762 306 675 (DeryCode)\n2. Send the payment confirmation to WhatsApp 0762 306 675\n3. Paste your licence reference below and tap Activate", color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(ref, { ref = it }, Modifier.width(260.dp), "Licence reference from DeryCode")
                Btn("Activate") {
                    if (ref.isBlank()) msg = "Paste the licence reference DeryCode sent you."
                    else {
                        val p = when {
                            ref.contains("MB", true) -> "Multi-Branch"
                            ref.contains("SC", true) -> "School"
                            ref.contains("GR", true) -> "Growth"
                            ref.contains("SD", true) -> "Standard"
                            ref.contains("ST", true) -> "Starter"
                            else -> rec
                        }
                        state.repo.mutate("LICENCE_ACTIVATED", "AppSettings", "licence", new = p) { dd ->
                            dd.copy(settings = dd.settings.copy(licencePlan = p, licenceRef = ref.trim()))
                        }
                        msg = "Activated: $p ✓"
                        state.refresh()
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = Theme.GOOD, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PlanCard(name: String, price: String, per: String, features: List<String>, current: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.background(if (current) Theme.ACCENT_SOFT else Theme.CARD, RoundedCornerShape(14.dp))
            .border(1.dp, if (current) Theme.ACCENT else Color(0xFF1E2A47), RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = if (current) Theme.ACCENT else Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (current) Text("ACTIVE", color = Theme.GOOD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(price, color = Theme.TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(per, color = Theme.MUTED, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        features.forEach { Text("✓ $it", color = Theme.TEXT, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Help & Documentation — user guide, privacy policy, terms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HelpDocsScreen(state: AppState) {
    var tab by remember { mutableStateOf("guide") }
    ScreenTitle("Help & Documentation", "The full manual, privacy policy and terms — readable right here, offline.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("training" to "Training Slides", "guide" to "User Guide", "privacy" to "Privacy Policy", "terms" to "Terms & Conditions").forEach { (k, label) ->
                FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(label, fontSize = 13.sp) })
            }
        }
        CardBox {
            when (tab) {
                "training" -> DocTraining()
                "guide" -> DocGuide()
                "privacy" -> DocText(DocsText.PRIVACY)
                "terms" -> DocText(DocsText.TERMS)
            }
        }
    }
}

@Composable
private fun DocGuide() {
    val sections = listOf(
        "1. Getting started" to "Go to School Setup and save your school profile, then create an academic year and add its 3 terms. Create classes in Classes & Streams, then add students and enroll them.",
        "2. Subjects & grading" to "Subjects come preloaded — edit them to match your school. Grading Schemes hold the PLE/UCE/UACE boundaries; create a new version any time. Old reports keep the scheme they were printed with.",
        "3. Entering marks" to "Open Marks Grid, pick a class and subject, type values and press Save all entered marks. Use ABS for absent, EXEMPT for exempted students. Submit sheet sends it for review.",
        "4. Fee collection" to "Use the School fees, Boarding fees and Bursary tabs as separate ledgers. Set an amount per class and term, then record payments under the matching ledger. Each report card shows independent paid and balance figures. See docs/FEE-COLLECTION.md for the full operating procedure.",
        "5. Reports" to "In Reports, pick class + term + template and generate real Word (.docx) report cards. Use Report Preview to check one student first. Generated reports are archived in Report Archive.",
        "6. Backup — never lose data" to "Every change is saved instantly with a rotating backup (Backup History). Copy the data file to a USB stick for off-site safety, or restore any previous backup in one click.",
        "7. Teacher phones" to "Sync & Backup exports a school config file (no marks). Copy it to a teacher's phone, then import in the teacher app. Teachers enter marks offline and bring back a bundle file you import.",
        "8. Troubleshooting" to "If the console ever crashes, a crash-log.txt appears in your SchoolReportMaker folder (in your user folder) with a one-tap WhatsApp send. If a screen looks wrong, close and reopen the console — your data is always saved."
    )
    LazyColumn(Modifier.height(430.dp)) {
        items(sections) { (title, body) ->
            Text(title, color = Theme.ACCENT, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
            Text(body, color = Theme.TEXT, fontSize = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Training slides — the staff-training deck, inside the app (offline)
// ─────────────────────────────────────────────────────────────────────────────
private data class TrainSlide(val title: String, val sub: String, val points: List<String>, val tip: String = "")

private val TRAINING = listOf(
    TrainSlide("First launch — the Setup Wizard", "Opens automatically the first time. Four short steps:",
        listOf("School profile — name, district, head teacher (printed on every report)",
            "Online account — email + password; your school code for teachers is created here",
            "Security PIN — locks the console; you type it every time you open the app",
            "Licence — enter your plan key, or start a free 30-day trial"),
        "If you skip the online account you can set it up later in Cloud & Online."),
    TrainSlide("Set up your school", "Before any marks: academic year, classes, students.",
        listOf("School Setup → save your profile",
            "Create the academic year and its 3 terms (Term 1 is set current)",
            "Classes & Streams → create classes per level (S1–S6 / P1–P7)",
            "Students → add one by one, or paste an entire list from Excel/CSV",
            "Each student is enrolled into a class for the year"),
        "The bulk import accepts admission no, name, sex, phone — and tells you about duplicates."),
    TrainSlide("Enter marks — the Marks Grid", "Keyboard-first: type, Enter, repeat.",
        listOf("Pick a class on the left, then a subject",
            "Type each student's mark and press Enter to move down",
            "Special entries: ABS (absent) and EXEMPT",
            "Save all entered marks writes them instantly",
            "Submit sheet for review locks it for results"),
        "Marks validate against the subject's maximum — you cannot type impossible values."),
    TrainSlide("Timetable", "Build the weekly timetable; teachers see it on their phones.",
        listOf("Academics → Timetable",
            "Pick the day (Mon–Sat), type start and end times (24h, e.g. 08:00–09:00)",
            "Choose class, subject and teacher, then Add period",
            "Remove any period with one click",
            "Teachers receive it on Pull school setup in the phone app"),
        ""),
    TrainSlide("Teachers — staff, roles, duty", "Everything about your staff lives here.",
        listOf("Add teachers with their role (Teacher, Head Teacher, Admin, Data Entry)",
            "Assign teacher → subject → class (subject optional)",
            "Duty roster: put a teacher on duty for a date — appears on their phone",
            "Personal codes: generate a one-time code per teacher (Cloud & Online)"),
        "A personal code dies after first use — safe to send on WhatsApp."),
    TrainSlide("Reports — the end product", "Real Word (.docx) report cards, offline.",
        listOf("Reports → pick class + term + template",
            "Report Preview one student first to check the layout",
            "Generate the whole class in one click — each student gets an A4 card",
            "Marks, grades, aggregate/division, comments and signature lines",
            "Everything you generate is kept in Report Archive"),
        "Reports freeze the grading scheme — re-printing later never changes a printed result."),
    TrainSlide("Cloud & Online", "Off-site backup + the teacher phone link.",
        listOf("Back Up Now — your whole school data to the cloud",
            "Teacher school code — share on WhatsApp so teachers join",
            "Personal codes — one-time code per teacher",
            "Pull Teacher Submissions — imports marks, attendance, duty logs and gate passes from teacher phones",
            "Restore From Cloud — recover everything on a new computer"),
        "Open Cloud & Online at least once a day and pull teacher submissions."),
    TrainSlide("Never lose data", "Three layers of protection.",
        listOf("Every change saves instantly — no save button needed",
            "Rotating backups: restore any of the last versions in Backup History",
            "Cloud backup copies everything off-site daily (if connected)",
            "Copy the data file to a USB stick for extra safety"),
        "If the console ever crashes, crash-log.txt appears in your SchoolReportMaker folder with a one-tap WhatsApp send.")
)

@Composable
private fun DocTraining() {
    var i by remember { mutableStateOf(0) }
    val s = TRAINING[i]
    Column(Modifier.verticalScroll(rememberScrollState()).height(430.dp)) {
        Text(s.title, color = Theme.ACCENT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(s.sub, color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        s.points.forEach { p ->
            Text("•  $p", color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (s.tip.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("TIP — ${s.tip}", color = Theme.GOOD, fontSize = 13.sp,
                modifier = Modifier.background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)).padding(10.dp).fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("‹ Prev", primary = false) { if (i > 0) i-- }
            Text("Slide ${i + 1} of ${TRAINING.size}", color = Theme.MUTED, fontSize = 13.sp)
            Btn("Next ›") { if (i < TRAINING.size - 1) i++ }
        }
    }
}

@Composable
private fun DocText(text: String) {
    Column(Modifier.verticalScroll(rememberScrollState()).height(430.dp)) {
        text.split("\n\n").forEach { para ->
            Text(para, color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

object DocsText {
    val PRIVACY = """
DeryCode School Report Maker — Privacy Policy

Effective date: 1 September 2026

1. Offline by design. The admin console and teacher app store all school data (students, marks, fees, comments) in files on your own computer or phone. Nothing is uploaded to any server. The app works fully offline.

2. What we never collect. We do not collect, transmit or sell student data, marks, photos, contacts or any school records. Crash files are written only to your own device and are sent to support only when you choose to send them.

3. Update checks. When you are online, the app checks GitHub for a newer version. This request contains no school or student data — only the app's version number.

4. Third parties. We use GitHub (a Microsoft company) to host installers and updates. No other third party receives your data.

5. Your control. Your data stays in the SchoolReportMaker folder in your user directory. You can copy, back up or delete it at any time. Uninstalling the app does not delete your data.

6. Teachers & students. Personal data (names, marks, fee records) is processed by the school itself for its own reporting. DeryCode has no access to it.

7. Contact. Questions about this policy: WhatsApp +256 762 306 675 or derickasiimwe849@gmail.com.
""".trim()

    val TERMS = """
DeryCode School Report Maker — Terms & Conditions

Effective date: 1 September 2026

1. Licence. The Trial is free for 30 days. The School Licence (UGX 250,000/year) covers one school on unlimited school-owned computers. The Multi-Branch Licence (UGX 600,000/year) covers unlimited branches of one school. Each licence includes free updates while active.

2. Payment. Paid by MTN MoMo or Airtel Money to 0762 306 675. The licence reference sent after payment activates the plan in-app. Licences run one year from activation and are not auto-renewed.

3. What you may do. Install the licensed app on the school's own computers and teacher phones, make backups, and use generated reports freely for your school.

4. What you may not do. Resell, sublicense, or give the app to other schools; remove branding; or use it to provide a competing service.

5. Your data, your responsibility. The app stores data only on your devices. You are responsible for keeping backups (the app rotates automatic ones in the SchoolReportMaker/backups folder). DeryCode cannot recover data from devices we do not control.

6. Support. WhatsApp support during business hours. Priority support for paid licences. The app is offline software — availability depends on your devices.

7. Liability. To the extent permitted by law, DeryCode's total liability is limited to the licence fees paid in the current year. We are not liable for lost profits or data loss caused by failure to keep backups.

8. Changes. These terms may be updated with new versions of the app. Continued use after an update means you accept the updated terms.

9. Contact. WhatsApp +256 762 306 675 · derickasiimwe849@gmail.com.
""".trim()
}
