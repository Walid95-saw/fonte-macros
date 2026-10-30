package com.fontemacros.app

import android.app.Activity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView

/**
 * Health Connect exige qu'une application qui lit des donnees de sante puisse expliquer
 * pourquoi, dans un ecran a elle. Le systeme ouvre cette activite depuis l'ecran des
 * autorisations ; elle n'est jamais lancee par l'utilisateur directement.
 */
class PolitiqueActivity : Activity() {

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        val texte = TextView(this).apply {
            setPadding(48, 64, 48, 48)
            textSize = 15f
            movementMethod = ScrollingMovementMethod()
            text = """
                Fonte & Macros — utilisation des donnees de sante

                L'application lit, en lecture seule :
                  • les calories actives depensees
                  • la distance parcourue
                  • le nombre de pas
                  • la duree de sommeil
                  • la frequence cardiaque moyenne

                A quoi elles servent
                Ces mesures s'affichent dans l'onglet « Montre & sante ». Si tu actives la
                cible calorique dynamique, la depense active du jour sert a calculer ton
                objectif de calories a la place d'un facteur d'activite estime.

                Ou elles vont
                Nulle part. Elles restent sur ce telephone, dans le stockage prive de
                l'application. Rien n'est envoye sur un serveur, rien n'est partage avec un
                tiers, et l'application n'ecrit jamais dans Health Connect.

                Comment revenir en arriere
                Tu peux retirer chaque autorisation a tout moment depuis Health Connect,
                dans les parametres du telephone. L'application continue de fonctionner
                sans ces donnees.
            """.trimIndent()
        }
        setContentView(texte)
    }
}
