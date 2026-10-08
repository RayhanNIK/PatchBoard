package com.example.patchboard.data

import com.example.patchboard.model.*
import com.example.patchboard.model.LocationTag.*
import com.example.patchboard.model.Priority.*
import java.util.Calendar

/** Same demo content as the React prototype. */
object SeedData {

    private fun hoursAgo(hours: Long) = System.currentTimeMillis() - hours * 3_600_000L

    /** Builds a comment stamped at "9:14 AM"-style time today (or yesterday if that is in the future). */
    private fun mc(id: String, author: String, text: String, time: String): Comment {
        val match = Regex("""(\d+):(\d+)\s*(AM|PM)""", RegexOption.IGNORE_CASE).find(time)
        val cal = Calendar.getInstance()
        if (match != null) {
            var hour = match.groupValues[1].toInt() % 12
            if (match.groupValues[3].uppercase() == "PM") hour += 12
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, match.groupValues[2].toInt())
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return Comment(id, author, text, cal.timeInMillis)
    }

    fun columns(): List<Column> = listOf(
        Column(Stage.REPORTED, listOf(
            ReportCard(1, IT_LAB, "Projector Not Working",
                "Projector in Lab 3B fails to display HDMI input from any laptop.",
                HIGH, 14, listOf(
                    mc("c1", "Rere", "Checked this morning — the HDMI port on the wall panel feels loose.", "9:14 AM"),
                    mc("c2", "James K.", "Confirmed, same issue with my MacBook and a Dell yesterday.", "9:31 AM"),
                    mc("c3", "Priya S.", "IT said replacement cable arrives Thursday.", "11:02 AM"),
                ), hoursAgo(3)),
            ReportCard(2, LIBRARY, "AC Unit Leaking",
                "Water dripping from central AC unit onto reading desks near window.",
                MEDIUM, 8, listOf(
                    mc("c4", "Amir T.", "Visible water stain on desk row 4. I put a bucket under it for now.", "8:50 AM"),
                    mc("c5", "Rere", "Good call. Facilities said they'll look at it today.", "10:15 AM"),
                ), hoursAgo(30)),
            ReportCard(3, CAFETERIA, "Broken Chair Legs",
                "Three chairs at table row F have cracked legs and are unsafe to sit on.",
                LOW, 5, listOf(
                    mc("c6", "Lena M.", "Tagged the three chairs with red tape so nobody sits on them.", "1:22 PM"),
                ), hoursAgo(10 * 24)),
            ReportCard(4, GYM, "Treadmill Belt Slipping",
                "Treadmill #4 belt loses tension and slips during use above 8 km/h.",
                HIGH, 22, listOf(
                    mc("c7", "Rere", "I almost fell off this one. Needs urgent attention.", "7:05 AM"),
                    mc("c8", "Coach Dan", "Treadmill #4 taken out of service until fixed.", "7:48 AM"),
                    mc("c9", "James K.", "Has maintenance been notified? This is a safety hazard.", "8:20 AM"),
                    mc("c10", "Coach Dan", "Yes, work order submitted. Waiting on parts.", "8:35 AM"),
                ), hoursAgo(6 * 24)),
        )),
        Column(Stage.IN_PROGRESS, listOf(
            ReportCard(5, LECTURE_HALL, "Microphone Feedback",
                "Wireless mic in Hall A produces loud feedback when near rear speakers.",
                HIGH, 31, listOf(
                    mc("c11", "Prof. Osei", "Disrupted my entire 9 AM lecture. Very distressing for students.", "10:00 AM"),
                    mc("c12", "AV Team", "Identified gain loop. Reducing speaker output as temporary fix.", "11:30 AM"),
                    mc("c13", "Rere", "ETA on permanent fix? We have a presentation Friday.", "12:05 PM"),
                    mc("c14", "AV Team", "New mic receiver ordered, install by Thursday EOD.", "12:44 PM"),
                ), hoursAgo(5 * 7 * 24)),
            ReportCard(6, IT_LAB, "Network Switch Offline",
                "Switch cabinet B2 offline since Tuesday, affecting 24 workstations.",
                HIGH, 19, listOf(
                    mc("c15", "Netops", "Power cycle failed. Suspect bad SFP module on port 12.", "2:10 PM"),
                    mc("c16", "Rere", "Can affected users get temporary hotspot access?", "2:35 PM"),
                    mc("c17", "Netops", "Replacement module on order. ETA 48 hours.", "3:00 PM"),
                ), hoursAgo(3 * 24)),
            ReportCard(7, RESTROOM, "Soap Dispenser Broken",
                "Dispenser on 2nd floor west wing empty and nozzle is cracked.",
                MEDIUM, 7, listOf(
                    mc("c18", "Housekeeping", "Replacement dispenser on order. Will install when stock arrives.", "9:00 AM"),
                ), hoursAgo(2 * 30 * 24)),
        )),
        Column(Stage.FIXED, listOf(
            ReportCard(8, LIBRARY, "Printer Paper Jam",
                "Recurring jam in upper tray of Canon printer near study pods.",
                LOW, 4, listOf(
                    mc("c19", "IT Help", "Replaced worn pickup rollers and cleaned paper path. Tested OK.", "3:45 PM"),
                    mc("c20", "Rere", "Works perfectly now, thanks for the quick turnaround!", "4:10 PM"),
                ), hoursAgo(8 * 30 * 24)),
            ReportCard(9, CAFETERIA, "Flickering Lights",
                "Fluorescent tube row C3 flickering due to faulty ballast. Replaced.",
                MEDIUM, 11, listOf(
                    mc("c21", "Facilities", "Ballast replaced, new T8 tubes fitted. No further flickering.", "11:00 AM"),
                ), hoursAgo(13 * 30 * 24)),
            ReportCard(10, GYM, "Fire Exit Sign Out",
                "Emergency sign above rear exit repowered and tested green.",
                HIGH, 26, listOf(
                    mc("c22", "Safety Mgr", "Rewired junction box. Sign tested under load — all green.", "2:00 PM"),
                    mc("c23", "Rere", "Great, this was a real safety concern. Glad it is resolved.", "2:18 PM"),
                ), hoursAgo(2 * 365 * 24)),
            ReportCard(11, LECTURE_HALL, "Broken Window Latch",
                "Window in Hall C row 4 now secured with new latch hardware.",
                LOW, 3, emptyList(), hoursAgo(4 * 7 * 24)),
        )),
    )
}
