/* Relie l'application web au greffon natif de lecture sante.
   Dans un navigateur ordinaire, window.Capacitor n'existe pas : window.Sante reste
   indefini et l'onglet « Montre & sante » affiche simplement ou trouver l'application. */
(function () {
  var greffon = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Sante;
  if (!greffon) return;

  window.Sante = {
    disponible: function () {
      return greffon.disponible();
    },
    permissions: function () {
      return greffon.permissions().then(function (r) { return !!(r && r.autorise); });
    },
    jours: function (debut, fin) {
      return greffon.jours({ debut: debut, fin: fin }).then(function (r) {
        return (r && r.jours) || [];
      });
    },
    seances: function (debut, fin) {
      return greffon.seances({ debut: debut, fin: fin }).then(function (r) {
        return (r && r.seances) || [];
      });
    },
    ouvrirReglages: function () {
      return greffon.ouvrirReglages();
    }
  };
})();
