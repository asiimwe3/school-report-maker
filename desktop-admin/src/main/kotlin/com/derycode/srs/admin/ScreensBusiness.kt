package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
    var feeAmount by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var payStudent by remember { mutableStateOf("") }
    var payAmount by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf("Mobile Money") }
    var payReceipt by remember { mutableStateOf("") }

    val cls = d.classes.firstOrNull { it.id == classId }
    val students = d.enrollments.filter { it.classId == classId && (year == null || it.academicYearId == year.id) }
        .mapNotNull { e -> d.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }
    val structure = d.feeStructures.firstOrNull { it.classId == classId && term != null && it.termId == term.id }
    val cur = d.settings.currencySymbol

    ScreenTitle("School Fees", "Set the fee per class and term, record payments, see balances — appears on report cards.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        // ── Fee structure ──
        CardBox {
            Text("Fee structure (${term?.let { "Term ${it.number} ${year?.year}" } ?: "no term"})", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (d.classes.isEmpty()) Text("Add classes first.", color = Theme.MUTED, fontSize = 12.sp)
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.classes.filter { it.active }.take(10).forEach { c ->
                        FilterChip(selected = classId == c.id, onClick = { classId = c.id },
                            label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 10.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(if (structure != null && feeAmount.isBlank()) structure.amount.toLong().toString() else feeAmount,
                        { feeAmount = it }, Modifier.width(160.dp), "Amount per term")
                    Text(cur, color = Theme.MUTED, fontSize = 12.sp)
                    Btn(if (structure == null) "Set fee" else "Update fee") {
                        val amt = feeAmount.toDoubleOrNull() ?: structure?.amount
                        if (classId.isNotBlank() && term != null && amt != null && amt > 0) {
                            val id = structure?.id ?: state.repo.nextId()
                            state.repo.mutate("FEE_STRUCTURE_SET", "FeeStructure", id, new = "$amt") { dd ->
                                dd.copy(feeStructures = (dd.feeStructures.filter { !(it.classId == classId && it.termId == term.id) } +
                                    FeeStructure(id = id, classId = classId, academicYearId = year?.id ?: "", termId = term.id, amount = amt)))
                            }
                            feeAmount = ""; msg = "Fee set for ${cls?.name}: ${cur} ${amt.toLong()}"
                            state.refresh()
                        }
                    }
                    if (structure != null) Text("Current: ${cur} ${structure.amount.toLong()}", color = Theme.GOOD, fontSize = 12.sp)
                }
            }
        }

        // ── Record payment ──
        if (students.isNotEmpty()) {
            CardBox {
                Text("Record a payment", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    students.take(20).forEach { s ->
                        FilterChip(selected = payStudent == s.id, onClick = { payStudent = s.id },
                            label = { Text(s.fullName.take(14), fontSize = 10.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Amount ($cur)"); TextField(payAmount, { payAmount = it }, Modifier.fillMaxWidth(), "e.g. 150000") }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Method")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Mobile Money", "Cash", "Bank").forEach { m ->
                                FilterChip(selected = payMethod == m, onClick = { payMethod = m }, label = { Text(m, fontSize = 10.sp) })
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
                                    amount = amt, date = System.currentTimeMillis(), method = payMethod, receipt = payReceipt))
                            }
                            payAmount = ""; payReceipt = ""
                            msg = "Payment recorded: ${cur} ${amt.toLong()} for ${s?.fullName}"
                            state.refresh()
                        }
                    }
                    if (msg.isNotBlank()) Text(msg, color = Theme.GOOD, fontSize = 12.sp)
                }
            }
        }

        // ── Balances ──
        CardBox {
            Text("Balances — ${cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "select a class"}",
                color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (structure == null) Text("No fee set for this class/term yet — set one above.", color = Theme.MUTED, fontSize = 12.sp)
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
                        val paid = d.feePayments.filter { it.studentId == s.id && it.termId == term?.id }.sumOf { it.amount }
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
                val outstanding = students.sumOf { s -> (structure.amount - d.feePayments.filter { it.studentId == s.id && it.termId == term?.id }.sumOf { it.amount }).coerceAtLeast(0.0) }
                Text("Expected: $cur ${expected.toLong()}    Collected: $cur ${totalPaid.toLong()}    Outstanding: $cur ${outstanding.toLong()}", color = Theme.GOOD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Btn("Print defaulter list (Word)", primary = false, onClick = {
                    val rows = students.mapNotNull { s ->
                        val paid = d.feePayments.filter { it.studentId == s.id && it.termId == term?.id }.sumOf { it.amount }
                        val bal = structure.amount - paid
                        if (bal > 0) listOf(s.fullName, structure.amount.toLong().toString(), paid.toLong().toString(), bal.toLong().toString()) else null
                    }
                    val label = cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "Class"
                    val f = dataDir.resolve("reports").resolve("defaulters-${label.replace(" ", "")}-term${term?.number}.docx")
                    DocxReport.writeDefaulterList(f, d, label, "Term ${term?.number} ${year?.year ?: ""}",
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        CardBox {
            Text("Your school: $n students → recommended plan: $rec", color = Theme.ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            plans.forEach { (name, feats) ->
                val pretty = name.lowercase().replaceFirstChar { it.uppercase() }.replace("-", " ")
                val isTrial = plan == "Trial"
                PlanCard(name, feats[1], feats[0], feats, plan.equals(pretty, true), Modifier.width(240.dp))
            }
        }
        CardBox {
            Text("Current plan: $plan", color = if (plan == "Trial") Theme.WARN else Theme.GOOD, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (d.settings.licenceRef.isNotBlank()) Text("Licence ref: ${d.settings.licenceRef}", color = Theme.MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("How to pay", color = Theme.TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("1. MTN MoMo / Airtel Money to 0762 306 675 (DeryCode)\n2. Send the payment confirmation to WhatsApp 0762 306 675\n3. Paste your licence reference below and tap Activate", color = Theme.MUTED, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
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
                if (msg.isNotBlank()) Text(msg, color = Theme.GOOD, fontSize = 12.sp)
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
            Text(name, color = if (current) Theme.ACCENT else Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (current) Text("ACTIVE", color = Theme.GOOD, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(price, color = Theme.TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(per, color = Theme.MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        features.forEach { Text("✓ $it", color = Theme.TEXT, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
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
            listOf("guide" to "User Guide", "privacy" to "Privacy Policy", "terms" to "Terms & Conditions").forEach { (k, label) ->
                FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        CardBox {
            when (tab) {
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
        "4. School fees" to "In School Fees, set the amount per class and term, record payments as they come in. Fee, paid and balance appear automatically on each student's report card.",
        "5. Reports" to "In Reports, pick class + term + template and generate real Word (.docx) report cards. Use Report Preview to check one student first. Generated reports are archived in Report Archive.",
        "6. Backup — never lose data" to "Every change is saved instantly with a rotating backup (Backup History). Copy the data file to a USB stick for off-site safety, or restore any previous backup in one click.",
        "7. Teacher phones" to "Sync & Backup exports a school config file (no marks). Copy it to a teacher's phone, then import in the teacher app. Teachers enter marks offline and bring back a bundle file you import.",
        "8. Troubleshooting" to "If the console ever crashes, a crash-log.txt appears in your SchoolReportMaker folder (in your user folder) with a one-tap WhatsApp send. If a screen looks wrong, close and reopen the console — your data is always saved."
    )
    LazyColumn(Modifier.height(430.dp)) {
        items(sections) { (title, body) ->
            Text(title, color = Theme.ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
            Text(body, color = Theme.TEXT, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DocText(text: String) {
    Column(Modifier.verticalScroll(rememberScrollState()).height(430.dp)) {
        text.split("\n\n").forEach { para ->
            Text(para, color = Theme.TEXT, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
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
