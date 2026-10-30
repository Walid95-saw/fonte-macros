package com.fontemacros.app

import androidx.activity.result.ActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/**
 * Lecture seule des donnees de sante du telephone, via Health Connect.
 *
 * Health Connect est le service systeme d'Android qui regroupe ce qu'ecrivent les autres
 * applications : Samsung Health, Google Fit, Fitbit, Garmin. Rien ne sort de l'appareil ;
 * ce greffon ne fait que lire et rendre des totaux par journee.
 *
 * Huawei Sante n'ecrit pas dans Health Connect sur la plupart des appareils : il faudra
 * son propre service, qui demande un compte developpeur Huawei et une application declaree.
 */
@CapacitorPlugin(name = "Sante")
class SantePlugin : Plugin() {

    private val portee = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissions = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    private val client get() = HealthConnectClient.getOrCreate(context)

    private fun installe() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Pourquoi Health Connect n'est pas utilisable, en clair pour l'interface. */
    private fun raison() = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> "ok"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "mise_a_jour"
        else -> "absent"
    }

    private suspend fun toutAutorise() =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    override fun handleOnDestroy() {
        portee.cancel()
        super.handleOnDestroy()
    }

    /** Health Connect est-il present, et avons-nous deja le droit de lire ? */
    @PluginMethod
    fun disponible(call: PluginCall) {
        if (!installe()) {
            call.resolve(JSObject().put("ok", false).put("raison", raison()).put("source", "Health Connect"))
            return
        }
        portee.launch {
            try {
                val accordees = client.permissionController.getGrantedPermissions()
                val manquantes = JSArray()
                for (p in permissions - accordees) manquantes.put(p.substringAfterLast("."))
                call.resolve(
                    JSObject().put("ok", true)
                        .put("autorise", accordees.containsAll(permissions))
                        .put("raison", "ok")
                        .put("manquantes", manquantes)
                        .put("source", "Health Connect")
                )
            } catch (e: Exception) {
                call.resolve(JSObject().put("ok", false).put("raison", "erreur").put("source", "Health Connect"))
            }
        }
    }

    /** Ouvre l'ecran Health Connect du systeme, ou sa fiche sur le magasin s'il manque. */
    @PluginMethod
    fun ouvrirReglages(call: PluginCall) {
        val intention = if (installe()) {
            android.content.Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        } else {
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
            )
        }
        intention.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intention)
            call.resolve(JSObject().put("ouvert", true))
        } catch (e: Exception) {
            call.resolve(JSObject().put("ouvert", false))
        }
    }

    /** Ouvre l'ecran systeme ou l'utilisateur choisit ce qu'il partage. */
    @PluginMethod
    fun permissions(call: PluginCall) {
        if (!installe()) {
            call.resolve(JSObject().put("autorise", false))
            return
        }
        val contrat = PermissionController.createRequestPermissionResultContract()
        startActivityForResult(call, contrat.createIntent(context, permissions), "retourPermissions")
    }

    @ActivityCallback
    private fun retourPermissions(call: PluginCall?, resultat: ActivityResult) {
        if (call == null) return
        portee.launch {
            try {
                call.resolve(JSObject().put("autorise", toutAutorise()))
            } catch (e: Exception) {
                call.resolve(JSObject().put("autorise", false))
            }
        }
    }

    /**
     * Les seances d'exercice enregistrees par la montre, avec la depense active
     * mesuree pendant chacune. C'est le seul chiffre comparable a l'estimation de
     * l'application : le total de la journee contient aussi la marche et le menage.
     */
    @PluginMethod
    fun seances(call: PluginCall) {
        val debutTexte = call.getString("debut")
        val finTexte = call.getString("fin")
        if (debutTexte == null || finTexte == null) {
            call.reject("debut et fin sont requis"); return
        }
        if (!installe()) { call.reject("Health Connect n'est pas installe"); return }
        portee.launch {
            try {
                val debut = LocalDate.parse(debutTexte).atStartOfDay()
                val fin = LocalDate.parse(finTexte).plusDays(1).atStartOfDay()
                val lues = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(debut, fin)
                    )
                ).records
                val tableau = JSArray()
                for (s in lues) {
                    val o = JSObject()
                    val zone = s.startZoneOffset ?: java.time.ZoneId.systemDefault().rules.getOffset(s.startTime)
                    o.put("date", s.startTime.atOffset(zone).toLocalDate().toString())
                    o.put("minutes", java.time.Duration.between(s.startTime, s.endTime).toMinutes())
                    o.put("genre", s.exerciseType)
                    o.put("titre", s.title ?: "")
                    /* la depense mesuree sur l'intervalle exact de la seance */
                    val kcal = try {
                        client.aggregate(
                            AggregateRequest(
                                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime)
                            )
                        )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    } catch (e: Exception) { null }
                    if (kcal != null) o.put("kcal", kcal)
                    tableau.put(o)
                }
                call.resolve(JSObject().put("seances", tableau))
            } catch (e: Exception) {
                call.reject(e.message ?: "lecture impossible")
            }
        }
    }

    /**
     * Totaux par journee entre deux dates incluses, au format AAAA-MM-JJ.
     * Une cle absente signifie qu'aucune mesure n'existe ce jour-la : on ne renvoie pas zero,
     * pour que l'interface distingue « rien mesure » de « vraiment zero ».
     */
    @PluginMethod
    fun jours(call: PluginCall) {
        val debutTexte = call.getString("debut")
        val finTexte = call.getString("fin")
        if (debutTexte == null || finTexte == null) {
            call.reject("debut et fin sont requis")
            return
        }
        if (!installe()) {
            call.reject("Health Connect n'est pas installe")
            return
        }
        portee.launch {
            try {
                val debut = LocalDate.parse(debutTexte)
                val fin = LocalDate.parse(finTexte)
                val groupes = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            DistanceRecord.DISTANCE_TOTAL,
                            StepsRecord.COUNT_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            SleepSessionRecord.SLEEP_DURATION_TOTAL
                        ),
                        timeRangeFilter = TimeRangeFilter.between(
                            debut.atStartOfDay(),
                            fin.plusDays(1).atStartOfDay()
                        ),
                        timeRangeSlicer = Period.ofDays(1)
                    )
                )
                val tableau = JSArray()
                for (g in groupes) {
                    val jour = JSObject().put("date", g.startTime.toLocalDate().toString())
                    var quelqueChose = false
                    g.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                        jour.put("kcal", it.inKilocalories); quelqueChose = true
                    }
                    g.result[DistanceRecord.DISTANCE_TOTAL]?.let {
                        jour.put("dist", it.inMeters); quelqueChose = true
                    }
                    g.result[StepsRecord.COUNT_TOTAL]?.let {
                        jour.put("pas", it); quelqueChose = true
                    }
                    g.result[HeartRateRecord.BPM_AVG]?.let {
                        jour.put("fc", it); quelqueChose = true
                    }
                    g.result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let {
                        jour.put("sommeil", it.toMinutes()); quelqueChose = true
                    }
                    if (quelqueChose) tableau.put(jour)
                }
                call.resolve(JSObject().put("jours", tableau))
            } catch (e: Exception) {
                call.reject(e.message ?: "lecture impossible")
            }
        }
    }
}
