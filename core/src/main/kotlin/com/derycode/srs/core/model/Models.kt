package com.derycode.srs.core.model

import kotlinx.serialization.Serializable

@Serializable
data class School(
    val name: String = "My School",
    val address: String = "",
    val motto: String = "",
    val emblemPath: String? = null,
    val headTeacher: String = ""
)

@Serializable
data class Teacher(
    val id: String,
    val name: String,
    val phone: String = "",
    val classIds: List<String> = emptyList(),
    val subjectIds: List<String> = emptyList()
)

@Serializable
data class SchoolClass(
    val id: String,
    val name: String,          // e.g. "Primary 7"
    val stream: String = "",   // e.g. "A"
    val classTeacherId: String? = null
)

@Serializable
data class Student(
    val id: String,
    val classId: String,
    val studentNo: String,
    val firstName: String,
    val lastName: String,
    val sex: String = "M",     // M / F
    val dateOfBirth: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val active: Boolean = true
)

@Serializable
data class Subject(
    val id: String,
    val code: String,          // e.g. "MTC"
    val name: String,          // e.g. "Mathematics"
    val compulsory: Boolean = true
)

@Serializable
data class MarkEntry(
    val studentId: String,
    val subjectId: String,
    val term: Int,             // 1..3
    val assessment: String,    // "BOT" | "MID" | "EOT"
    val score: Double          // 0..100
)

/** The complete offline dataset — one file is the whole database. */
@Serializable
data class SchoolData(
    val schemaVersion: Int = 1,
    val school: School = School(),
    val teachers: List<Teacher> = emptyList(),
    val classes: List<SchoolClass> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val students: List<Student> = emptyList(),
    val marks: List<MarkEntry> = emptyList()
)
