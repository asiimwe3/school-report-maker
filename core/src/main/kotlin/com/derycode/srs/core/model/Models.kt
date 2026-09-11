package com.derycode.srs.core.model

import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Enums — shared vocabulary (Section 57: one terminology everywhere)
// ─────────────────────────────────────────────────────────────────────────────

enum class Level { PRIMARY, O_LEVEL, A_LEVEL }

enum class StudentStatus { ACTIVE, TRANSFERRED, WITHDRAWN, GRADUATED, SUSPENDED, DECEASED }

/** Distinguishes real values from special cases (Section 16). */
enum class MarkType { VALUE, ABS, MISSING, EXEMPT, NA }

enum class MarkSheetState { DRAFT, SUBMITTED, REVIEWED, APPROVED, LOCKED }

enum class Role { SUPER_ADMIN, SCHOOL_ADMIN, HEAD_TEACHER, TEACHER, DATA_ENTRY }

enum class TemplateLayout { CLASSIC, MODERN, COMPACT, PLAIN }

enum class CommentType { SUBJECT, CLASS_TEACHER, HEAD_TEACHER }

// ─────────────────────────────────────────────────────────────────────────────
// School & academic calendar
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class School(
    val id: String = "school",
    val name: String = "My School",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val motto: String = "",
    val regNo: String = "",
    val schoolType: String = "",
    val headTeacher: String = "",
    val logoPath: String? = null,
    val stampPath: String? = null,
    val colorPrimary: String = "#1F6FEB",
    val colorSecondary: String = "#111827"
)

@Serializable
data class Term(
    val id: String,
    val yearId: String,
    val number: Int,                 // 1..3
    val startDate: String = "",
    val endDate: String = "",
    val nextTermBegins: String = "",
    val status: String = "OPEN"      // OPEN / CLOSED
)

@Serializable
data class AcademicYear(
    val id: String,
    val year: String,                // "2026"
    val terms: List<Term> = emptyList(),
    val currentTermId: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Structure: level → class → stream; A-level combinations
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SchoolClass(
    val id: String,
    val name: String,                // "Senior 1", "Primary 7"
    val level: Level = Level.O_LEVEL,
    val stream: String = "",         // "East", "West" — empty means no streams
    val classTeacherId: String? = null,
    val combinationId: String? = null,   // A-level classes follow a combination
    val active: Boolean = true
)

@Serializable
data class Combination(
    val id: String,
    val name: String,                // PCM / PCB / HEG / ECB
    val level: Level = Level.A_LEVEL,
    val principalSubjectIds: List<String> = emptyList(),
    val subsidiarySubjectIds: List<String> = emptyList()   // GP, Sub-ICT, SRE
)

// ─────────────────────────────────────────────────────────────────────────────
// People
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Student(
    val id: String,
    val admissionNo: String,
    val firstName: String,
    val middleName: String = "",
    val lastName: String,
    val sex: String = "M",           // M / F
    val dateOfBirth: String = "",
    val photoPath: String? = null,
    val guardianName: String = "",
    val guardianPhone: String = "",
    val address: String = "",
    val status: StudentStatus = StudentStatus.ACTIVE
) {
    val fullName: String get() = listOfNotNull(firstName, middleName, lastName).joinToString(" ")
}

/** Enrollment keeps history: a student belongs to a class only for a given year (Section 33). */
@Serializable
data class Enrollment(
    val id: String,
    val studentId: String,
    val academicYearId: String,
    val classId: String,
    val combinationId: String? = null
)

@Serializable
data class Teacher(
    val id: String,
    val name: String,
    val phone: String = "",
    val photoPath: String? = null,
    val signaturePath: String? = null,
    val role: Role = Role.TEACHER,
    val username: String = "",
    val pinHash: String = "",
    val active: Boolean = true
)

@Serializable
data class TeacherAssignment(
    val id: String,
    val teacherId: String,
    val subjectId: String? = null,   // null = class teacher of the class
    val classId: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Subjects & assessment
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Subject(
    val id: String,
    val name: String,
    val code: String = "",
    val level: Level = Level.O_LEVEL,
    val category: String = "",
    val compulsory: Boolean = true,
    val principal: Boolean = false,        // A-level principal subject
    val subsidiary: Boolean = false,       // A-level subsidiary
    val maxMarks: Int = 100,
    val active: Boolean = true
)

/** Assessment components (Section 13): configurable per school/level/subject. */
@Serializable
data class AssessmentComponent(
    val id: String,
    val code: String,                // BOT / MID / EOT / CA / AOI / PROJECT
    val name: String,
    val weightPercent: Int = 0,      // used by weighted schemes (e.g. UCE 20/80)
    val levels: List<Level> = emptyList(),
    val active: Boolean = true
)

/** Marks: one student, one subject, one term, one component. */
@Serializable
data class Mark(
    val id: String,
    val studentId: String,
    val subjectId: String,
    val termId: String,
    val componentId: String,
    val score: Double = 0.0,
    val type: MarkType = MarkType.VALUE,
    val maxScore: Int = 100,
    val enteredBy: String? = null,
    val updatedAt: Long = 0,
    val sheetState: MarkSheetState = MarkSheetState.DRAFT,
    val schemeVersionId: String? = null
)

/** Track per class+subject+term submission state (Section 17). */
@Serializable
data class MarkSheet(
    val id: String,
    val classId: String,
    val subjectId: String,
    val termId: String,
    val state: MarkSheetState = MarkSheetState.DRAFT
)

// ─────────────────────────────────────────────────────────────────────────────
// Grading — data, never hard-coded logic (Sections 18–19, 59)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class GradeBoundary(
    val minScore: Double,
    val maxScore: Double,
    val grade: String,               // "A", "1", "D" …
    val label: String = "",          // "Distinction", "Exceptional" …
    val points: Int = 0,
    val remark: String = ""          // auto remark text
)

/** How a level combines subject grades into an overall result. */
enum class AggregateRule { PLE_AGGREGATE, UCE_INDICATOR, UACE_POINTS, CUSTOM }

@Serializable
data class GradingScheme(
    val id: String,
    val name: String,
    val level: Level,
    val boundaries: List<GradeBoundary> = emptyList(),
    val aggregateRule: AggregateRule = AggregateRule.CUSTOM,
    // PLE
    val aggregateBestSubjects: Int = 4,          // PLE aggregate = best 4
    val divisionTable: List<DivisionBoundary> = emptyList(),
    // UCE competency-based
    val caWeightPercent: Int = 20,               // final = CA% + EOT%
    val examWeightPercent: Int = 80,
    // UACE
    val principalCount: Int = 3,
    val subsidiaryCounts: Boolean = true,       // subsidiary earns a point if better than configured threshold
    val subsidiaryThresholdGrade: String = "F",
    val version: Int = 1,
    val active: Boolean = true
)

@Serializable
data class DivisionBoundary(
    val maxAggregate: Int,           // inclusive upper bound
    val division: String,            // "Division 1" …
    val description: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Results (Section 20–21)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SubjectResult(
    val subjectId: String,
    val total: Double,
    val percentage: Double,
    val grade: String,
    val points: Int,
    val remark: String,
    val positionInSubject: Int = 0,  // 0 = not ranked
    val incomplete: Boolean = false  // missing/absent/exempt entries exist
)

@Serializable
data class TermResult(
    val studentId: String,
    val termId: String,
    val subjectResults: List<SubjectResult> = emptyList(),
    val averagePercent: Double = 0.0,
    val aggregate: Int? = null,      // PLE
    val division: String? = null,    // PLE
    val uceResult: String? = null,   // UCE Result 1 / 2 / 3
    val uacePoints: Int? = null,     // UACE
    val uaceResult: String? = null,  // UACE 1R/2R… derived
    val classPosition: Int = 0,
    val streamPosition: Int = 0,
    val state: MarkSheetState = MarkSheetState.DRAFT,
    val schemeId: String = "",
    val schemeVersion: Int = 1
)

// ─────────────────────────────────────────────────────────────────────────────
// Teacher-on-duty module: duty records, gate passes, enrollment requests
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class DutyIncident(
    val at: Long = 0,
    val note: String = ""
)

@Serializable
data class DutyRecord(
    val id: String,
    val teacherId: String,
    val date: String,                 // yyyy-MM-dd
    val role: String = "Teacher on duty",
    val incidents: List<DutyIncident> = emptyList()
)

@Serializable
data class GatePass(
    val id: String,
    val studentId: String,
    val teacherId: String,
    val reason: String = "",           // Sick bay / Home pass / Errand / Other
    val destination: String = "",
    val outAt: Long = 0,
    val expectedBack: String = "",
    val returnedAt: Long? = null       // null = still outside school
)

@Serializable
data class EnrollmentRequest(
    val id: String,
    val student: Student,
    val academicYearId: String = "",
    val classId: String,
    val teacherId: String,
    val status: String = "PENDING",    // PENDING / APPROVED
    val createdAt: Long = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Comments, signatures, templates, reports
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Comment(
    val id: String,
    val studentId: String,
    val termId: String,
    val subjectId: String? = null,  // null = class/head comment
    val type: CommentType,
    val text: String
)

@Serializable
data class ReportTemplate(
    val id: String,
    val name: String,
    val layout: TemplateLayout = TemplateLayout.CLASSIC,
    val level: Level? = null,        // null = any level
    val accentColor: String = "#1F6FEB",
    val isDefault: Boolean = false,
    val archived: Boolean = false
)

@Serializable
data class GeneratedReport(
    val id: String,
    val studentId: String,
    val termId: String,
    val templateId: String,
    val schemeId: String,
    val schemeVersion: Int,
    val resultSummary: String = "",  // frozen text of aggregate/division at approval time
    val approved: Boolean = false,
    val generatedAt: Long = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Audit & sync (Sections 42, 34–36)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AuditEntry(
    val id: String,
    val userId: String? = null,
    val action: String,              // MARK_ENTERED, MARK_CHANGED, UNLOCKED, SCHEME_CHANGED…
    val entity: String,
    val entityId: String,
    val oldValue: String = "",
    val newValue: String = "",
    val timestamp: Long = 0,
    val device: String = ""
)

@Serializable
data class SyncItem(
    val id: String,
    val entity: String,
    val entityId: String,
    val payloadJson: String,
    val status: String = "PENDING", // PENDING / SENT / FAILED / CONFLICT
    val attempts: Int = 0,
    val lastAttempt: Long = 0
)

@Serializable
data class ConflictRecord(
    val id: String,
    val studentId: String,
    val subjectId: String,
    val termId: String,
    val componentId: String,
    val localValue: String,
    val remoteValue: String,
    val resolved: Boolean = false,
    val resolvedValue: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// App settings & calendar (Admin screens: Settings, School Calendar)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AppSettings(
    val language: String = "English",
    val theme: String = "Dark",
    val currencySymbol: String = "UGX",
    val notificationsEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val autoBackupFrequencyDays: Int = 7,
    val backupsToKeep: Int = 10,
    val licencePlan: String = "Trial",
    val licenceRef: String = ""
)

// ── School fees (structure per class/term + payments per student) ──

@Serializable
data class FeeStructure(
    val id: String = "",
    val classId: String = "",
    val academicYearId: String = "",
    val termId: String = "",
    val name: String = "School fees",
    val amount: Double = 0.0
)

@Serializable
data class FeePayment(
    val id: String = "",
    val studentId: String = "",
    val academicYearId: String = "",
    val termId: String = "",
    val amount: Double = 0.0,
    val date: Long = 0,
    val method: String = "Cash",
    val receipt: String = "",
    val note: String = ""
)

enum class CalendarEventType { TERM_START, TERM_END, EXAM, HOLIDAY, MEETING, DEADLINE, OTHER }

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String,          // ISO yyyy-MM-dd
    val type: CalendarEventType = CalendarEventType.OTHER,
    val termId: String? = null,
    val notes: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Root database (Section 31–32) — everything in one JSON file, atomic writes
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SchoolData(
    val schemaVersion: Int = 2,
    val school: School = School(),
    val dutyRecords: List<DutyRecord> = emptyList(),
    val gatePasses: List<GatePass> = emptyList(),
    val enrollmentRequests: List<EnrollmentRequest> = emptyList(),
    val academicYears: List<AcademicYear> = emptyList(),
    val classes: List<SchoolClass> = emptyList(),
    val combinations: List<Combination> = emptyList(),
    val students: List<Student> = emptyList(),
    val enrollments: List<Enrollment> = emptyList(),
    val teachers: List<Teacher> = emptyList(),
    val assignments: List<TeacherAssignment> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val components: List<AssessmentComponent> = emptyList(),
    val marks: List<Mark> = emptyList(),
    val markSheets: List<MarkSheet> = emptyList(),
    val gradingSchemes: List<GradingScheme> = emptyList(),
    val termResults: List<TermResult> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val templates: List<ReportTemplate> = emptyList(),
    val reports: List<GeneratedReport> = emptyList(),
    val auditLog: List<AuditEntry> = emptyList(),
    val syncQueue: List<SyncItem> = emptyList(),
    val conflicts: List<ConflictRecord> = emptyList(),
    val lastSyncAt: Long = 0,
    val settings: AppSettings = AppSettings(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val feeStructures: List<FeeStructure> = emptyList(),
    val feePayments: List<FeePayment> = emptyList()
)
