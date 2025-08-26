package me.yacine.prayerReminder

import org.bukkit.plugin.java.JavaPlugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.bukkit.scheduler.BukkitRunnable
import java.time.LocalTime
import java.io.File
import org.bukkit.configuration.file.YamlConfiguration
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PrayerReminder : JavaPlugin() {
    override fun onEnable() {
        server.consoleSender.sendMessage("§a[Muslim Steve] §fPrayer Reminder Plugin is enabled!")

        // Register commands
        getCommand("setlocation")?.setExecutor(this)
        getCommand("getlocation")?.setExecutor(this)

        schedulePrayerTimeReminders()
    }

    override fun onCommand(
        sender: org.bukkit.command.CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (command.name.equals("setlocation", ignoreCase = true)) {
            if (args.size < 2) {
                sender.sendMessage("§c[Muslim Steve] §fUsage: /setlocation <latitude> <longitude>")
                sender.sendMessage("§c[Muslim Steve] §fU can get the latitude and longitude from https://www.latlong.net/")
                return true
            }

            try {
                val latitude = args[0]
                val longitude = args[1]

                // Basic validation
                val latValue = latitude.toDouble()
                val longValue = longitude.toDouble()

                if (latValue < -90 || latValue > 90 || longValue < -180 || longValue > 180) {
                    sender.sendMessage("§c[Muslim Steve] §fInvalid coordinates! Latitude must be between -90 and 90, longitude between -180 and 180.")
                    return true
                }

                saveLocationConfig(latitude, longitude)
                sender.sendMessage("§a[Muslim Steve] §fLocation set to: Lat=$latitude, Long=$longitude")

                // Reschedule prayer times with new location
                schedulePrayerTimeReminders()

                return true
            } catch (e: NumberFormatException) {
                sender.sendMessage("§c[Muslim Steve] §fInvalid coordinates! Please enter numbers.")
                return true
            }
        } else if (command.name.equals("getlocation", ignoreCase = true)) {
            val (latitude, longitude) = getLocationConfig()
            sender.sendMessage("§a[Muslim Steve] §fCurrent location: Lat=$latitude, Long=$longitude")
            return true
        }

        return false
    }

    fun getPrayerTimes(latitude: String, longitude: String): Map<String, String> {
        val data = mutableMapOf<String, String>()

        try {
            val dateString = LocalDateTime.now().toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))



             val url =
                java.net.URL("http://api.aladhan.com/v1/timings/$dateString?latitude=$latitude&longitude=$longitude&method=3")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"






            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }

                // Parse JSON response
                val jsonObject = org.json.JSONObject(response)
                val timings = jsonObject.getJSONObject("data").getJSONObject("timings")
                server.consoleSender.sendMessage(timings.toString())

                // Extract prayer times
                data["Lastthird"] = timings.getString("Lastthird")
                data["Fajr"] = timings.getString("Fajr")
                data["Sunrise"] = timings.getString("Sunrise")
                data["Dhuhr"] = timings.getString("Dhuhr")
                data["Asr"] = timings.getString("Asr")
                data["Maghrib"] = timings.getString("Maghrib")
                data["Isha"] = timings.getString("Isha")

            }
        } catch (e: Exception) {
            server.logger.warning("Error fetching prayer times: ${e.message}")
        }

        return data
    }

    fun schedulePrayerTimeReminders() {
        server.scheduler.cancelTasks(this)
        // Prayer messages map
        val prayerMessages = mapOf(
            "Fajr" to "🌅 Fajr has entered. Drop the game — rise for prayer before the day rises on you.",
            "Sunrise" to "☀️ Sunrise. Fajr is closed. If you missed it, repent and don't miss tomorrow.",
            "Dhuhr" to "🕛 Dhuhr time. Log out, bow down. Your soul needs this pause more than the game.",
            "Asr" to "🏞️ Asr has come. Don't gamble with time — pray before the sun slips away.",
            "Maghrib" to "🌇 Maghrib is in. Step away, face the qibla, not the screen.",
            "Isha" to "🌙 Isha time. End your day right — prayer before rest.",
            "Lastthird" to "🌌 Last third of the night. Rare bonus time: stand, pray, ask — doors of mercy are open."
        )

        // Run this as a repeating task to check and update prayer times daily
        object : BukkitRunnable() {
            override fun run() {
                val (latitude, longitude) = getLocationConfig()
                val prayerTimes = getPrayerTimes(latitude, longitude)
                val now = LocalDateTime.now()
                val currentDate = now.toLocalDate()

                for ((prayer, timeString) in prayerTimes) {
                    try {
                        // Convert time string (e.g. "05:30") to LocalTime
                        // Consider more robust time parsing
                        val prayerTime = try {
                            LocalTime.parse(timeString.substring(0, 5))
                        } catch (e: Exception) {
                            server.logger.warning("Failed to parse time: $timeString")
                            null
                        } ?: continue  // Skip this prayer if parsing fails

                        // Create LocalDateTime for this prayer time today
                        val prayerDateTime = currentDate.atTime(prayerTime)

                        // If prayer time hasn't passed today, schedule it
                        if (prayerDateTime.isAfter(now)) {
                            val delayInSeconds = java.time.Duration.between(now, prayerDateTime).seconds

                            // Schedule the prayer notification
                            server.scheduler.runTaskLater(this@PrayerReminder, Runnable {
                                // Send the custom message for this prayer
                                val message = prayerMessages[prayer] ?: "It's time for $prayer prayer!"
                                server.broadcastMessage("§a[Muslim Steve] §f$message")
                            }, delayInSeconds * 20L) // Convert seconds to ticks (20 ticks = 1 second)
                        }
                    } catch (e: Exception) {
                        server.logger.warning("Error scheduling $prayer prayer: ${e.message}")
                    }
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 60L * 60L) // Run every hour
    }

    fun saveLocationConfig(latitude: String, longitude: String) {
        try {
            // Create config file if it doesn't exist
            if (!dataFolder.exists()) {
                dataFolder.mkdir()
            }

            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration.loadConfiguration(configFile)

            // Set values
            config.set("latitude", latitude)
            config.set("longitude", longitude)

            // Save config
            config.save(configFile)
            server.consoleSender.sendMessage("§a[Muslim Steve] §fLocation saved: Lat=$latitude, Long=$longitude")
        } catch (e: Exception) {
            server.logger.warning("Error saving location config: ${e.message}")
        }
    }

    fun getLocationConfig(): Pair<String, String> {
        try {
            val configFile = File(dataFolder, "config.yml")

            // Default values if file doesn't exist
            if (!configFile.exists()) {
                return Pair("21.4225", "39.8262") // Mecca coordinates as default
            }

            val config = YamlConfiguration.loadConfiguration(configFile)
            val latitude = config.getString("latitude", "21.4225")
            val longitude = config.getString("longitude", "39.8262")

            return Pair(latitude!!, longitude!!)
        } catch (e: Exception) {
            server.logger.warning("Error reading location config: ${e.message}")
            return Pair("21.4225", "39.8262") // Default to Mecca coordinates
        }
    }

    override fun onDisable() {

    }
}
