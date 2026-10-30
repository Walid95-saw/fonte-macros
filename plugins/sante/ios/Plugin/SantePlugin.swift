import Foundation
import UIKit
import Capacitor
import HealthKit

/// Lecture seule des donnees de sante de l'iPhone, via HealthKit.
///
/// Sur iOS, tout passe par l'application Sante d'Apple, qui centralise ce qu'ecrivent
/// les montres et les applications tierces — Huawei Sante y ecrit, contrairement a
/// ce qu'elle fait sur Android. Il n'y a donc qu'un seul chemin a gerer ici.
///
/// Rien ne sort de l'appareil et l'application n'ecrit jamais dans Sante.
@objc(SantePlugin)
public class SantePlugin: CAPPlugin {

    private let magasin = HKHealthStore()

    /// Les types lus. L'ordre n'a pas d'importance, mais l'ensemble doit correspondre
    /// aux cles declarees dans Info.plist, sinon iOS refuse la demande sans explication.
    private var typesLus: Set<HKObjectType> {
        var t = Set<HKObjectType>()
        if let e = HKObjectType.quantityType(forIdentifier: .activeEnergyBurned) { t.insert(e) }
        if let d = HKObjectType.quantityType(forIdentifier: .distanceWalkingRunning) { t.insert(d) }
        if let p = HKObjectType.quantityType(forIdentifier: .stepCount) { t.insert(p) }
        if let f = HKObjectType.quantityType(forIdentifier: .heartRate) { t.insert(f) }
        if let s = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) { t.insert(s) }
        t.insert(HKObjectType.workoutType())
        return t
    }

    private var calendrier: Calendar { Calendar.current }

    private func jourDe(_ texte: String) -> Date? {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone.current
        return f.date(from: texte)
    }
    private func texteDe(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone.current
        return f.string(from: date)
    }

    // MARK: - Etat

    /// HealthKit existe-t-il sur cet appareil, et avons-nous deja le droit de lire ?
    ///
    /// Attention : iOS ne dit jamais si la lecture est autorisee, pour ne pas reveler
    /// qu'une donnee existe. On ne peut donc que detecter un refus explicite, et
    /// verifier autrement — en tentant une lecture — si l'acces fonctionne.
    @objc func disponible(_ call: CAPPluginCall) {
        guard HKHealthStore.isHealthDataAvailable() else {
            call.resolve(["ok": false, "raison": "absent", "source": "Apple Sante"])
            return
        }
        let refuses = typesLus.filter { magasin.authorizationStatus(for: $0) == .sharingDenied }
        let jamaisDemande = typesLus.allSatisfy { magasin.authorizationStatus(for: $0) == .notDetermined }
        call.resolve([
            "ok": true,
            "autorise": !jamaisDemande && refuses.isEmpty,
            "raison": "ok",
            "manquantes": refuses.map { $0.identifier },
            "source": "Apple Sante"
        ])
    }

    /// Ouvre la feuille systeme ou l'utilisateur choisit ce qu'il partage.
    @objc func permissions(_ call: CAPPluginCall) {
        guard HKHealthStore.isHealthDataAvailable() else {
            call.resolve(["autorise": false]); return
        }
        magasin.requestAuthorization(toShare: [], read: typesLus) { [weak self] accorde, erreur in
            guard let self = self else { return }
            if let erreur = erreur {
                call.reject(erreur.localizedDescription); return
            }
            let refuses = self.typesLus.filter { self.magasin.authorizationStatus(for: $0) == .sharingDenied }
            call.resolve(["autorise": accorde && refuses.isEmpty])
        }
    }

    /// Renvoie vers l'application Sante, ou l'utilisateur gere les acces.
    @objc func ouvrirReglages(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if let url = URL(string: "x-apple-health://"), UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                call.resolve(["ouvert": true])
            } else if let reglages = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(reglages)
                call.resolve(["ouvert": true])
            } else {
                call.resolve(["ouvert": false])
            }
        }
    }

    // MARK: - Totaux par journee

    /// Somme d'une quantite, decoupee par journee civile.
    private func parJour(_ id: HKQuantityTypeIdentifier, _ option: HKStatisticsOptions,
                         _ unite: HKUnit, _ debut: Date, _ fin: Date,
                         _ fini: @escaping ([String: Double]) -> Void) {
        guard let type = HKObjectType.quantityType(forIdentifier: id) else { fini([:]); return }
        var depart = DateComponents(); depart.day = 1
        let requete = HKStatisticsCollectionQuery(
            quantityType: type,
            quantitySamplePredicate: HKQuery.predicateForSamples(withStart: debut, end: fin),
            options: option,
            anchorDate: calendrier.startOfDay(for: debut),
            intervalComponents: depart)
        requete.initialResultsHandler = { _, resultats, _ in
            var sortie: [String: Double] = [:]
            resultats?.enumerateStatistics(from: debut, to: fin) { stat, _ in
                let q = option == .discreteAverage ? stat.averageQuantity() : stat.sumQuantity()
                if let q = q { sortie[self.texteDe(stat.startDate)] = q.doubleValue(for: unite) }
            }
            fini(sortie)
        }
        magasin.execute(requete)
    }

    /// Minutes de sommeil par journee, rattachees au jour du reveil.
    private func sommeilParJour(_ debut: Date, _ fin: Date, _ fini: @escaping ([String: Double]) -> Void) {
        guard let type = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) else { fini([:]); return }
        let requete = HKSampleQuery(sampleType: type,
                                    predicate: HKQuery.predicateForSamples(withStart: debut, end: fin),
                                    limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, echantillons, _ in
            var sortie: [String: Double] = [:]
            for e in (echantillons as? [HKCategorySample]) ?? [] {
                /* on ne compte que le sommeil reel, pas le temps passe au lit eveille */
                let dort: Bool
                if #available(iOS 16.0, *) {
                    dort = e.value == HKCategoryValueSleepAnalysis.asleepCore.rawValue
                        || e.value == HKCategoryValueSleepAnalysis.asleepDeep.rawValue
                        || e.value == HKCategoryValueSleepAnalysis.asleepREM.rawValue
                        || e.value == HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue
                } else {
                    dort = e.value == HKCategoryValueSleepAnalysis.asleep.rawValue
                }
                guard dort else { continue }
                let cle = self.texteDe(e.endDate)
                sortie[cle] = (sortie[cle] ?? 0) + e.endDate.timeIntervalSince(e.startDate) / 60
            }
            fini(sortie)
        }
        magasin.execute(requete)
    }

    @objc func jours(_ call: CAPPluginCall) {
        guard let d = call.getString("debut").flatMap(jourDe),
              let f = call.getString("fin").flatMap(jourDe) else {
            call.reject("debut et fin sont requis"); return
        }
        let debut = calendrier.startOfDay(for: d)
        let fin = calendrier.date(byAdding: .day, value: 1, to: calendrier.startOfDay(for: f)) ?? f

        let groupe = DispatchGroup()
        var kcal: [String: Double] = [:], dist: [String: Double] = [:]
        var pas: [String: Double] = [:], fc: [String: Double] = [:], sommeil: [String: Double] = [:]

        groupe.enter(); parJour(.activeEnergyBurned, .cumulativeSum, .kilocalorie(), debut, fin) { kcal = $0; groupe.leave() }
        groupe.enter(); parJour(.distanceWalkingRunning, .cumulativeSum, .meter(), debut, fin) { dist = $0; groupe.leave() }
        groupe.enter(); parJour(.stepCount, .cumulativeSum, .count(), debut, fin) { pas = $0; groupe.leave() }
        groupe.enter(); parJour(.heartRate, .discreteAverage,
                                HKUnit.count().unitDivided(by: .minute()), debut, fin) { fc = $0; groupe.leave() }
        groupe.enter(); sommeilParJour(debut, fin) { sommeil = $0; groupe.leave() }

        groupe.notify(queue: .main) {
            let dates = Set(kcal.keys).union(dist.keys).union(pas.keys).union(fc.keys).union(sommeil.keys)
            var tableau: [[String: Any]] = []
            for jour in dates.sorted() {
                var o: [String: Any] = ["date": jour]
                if let v = kcal[jour] { o["kcal"] = v }
                if let v = dist[jour] { o["dist"] = v }
                if let v = pas[jour] { o["pas"] = v }
                if let v = fc[jour] { o["fc"] = v }
                if let v = sommeil[jour] { o["sommeil"] = v }
                if o.count > 1 { tableau.append(o) }
            }
            call.resolve(["jours": tableau])
        }
    }

    // MARK: - Seances d'exercice

    /// Les entrainements enregistres, avec la depense mesuree pendant chacun.
    /// C'est le seul chiffre comparable a l'estimation de l'application : le total
    /// de la journee contient aussi la marche et les gestes du quotidien.
    @objc func seances(_ call: CAPPluginCall) {
        guard let d = call.getString("debut").flatMap(jourDe),
              let f = call.getString("fin").flatMap(jourDe) else {
            call.reject("debut et fin sont requis"); return
        }
        let debut = calendrier.startOfDay(for: d)
        let fin = calendrier.date(byAdding: .day, value: 1, to: calendrier.startOfDay(for: f)) ?? f

        let requete = HKSampleQuery(sampleType: HKObjectType.workoutType(),
                                    predicate: HKQuery.predicateForSamples(withStart: debut, end: fin),
                                    limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [weak self] _, echantillons, erreur in
            guard let self = self else { return }
            if let erreur = erreur { call.reject(erreur.localizedDescription); return }
            var tableau: [[String: Any]] = []
            for w in (echantillons as? [HKWorkout]) ?? [] {
                var o: [String: Any] = [
                    "date": self.texteDe(w.startDate),
                    "minutes": Int(w.duration / 60),
                    "genre": Int(w.workoutActivityType.rawValue)
                ]
                if #available(iOS 18.0, *) {
                    if let stat = w.statistics(for: HKQuantityType(.activeEnergyBurned)),
                       let somme = stat.sumQuantity() {
                        o["kcal"] = somme.doubleValue(for: .kilocalorie())
                    }
                } else if let brulees = w.totalEnergyBurned {
                    o["kcal"] = brulees.doubleValue(for: .kilocalorie())
                }
                tableau.append(o)
            }
            call.resolve(["seances": tableau])
        }
        magasin.execute(requete)
    }
}
