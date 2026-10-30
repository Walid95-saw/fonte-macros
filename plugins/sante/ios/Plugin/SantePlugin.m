#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Declare le greffon Swift au pont Capacitor. Sans ce fichier, la classe existe
// mais window.Capacitor.Plugins.Sante reste introuvable cote page web.
CAP_PLUGIN(SantePlugin, "Sante",
    CAP_PLUGIN_METHOD(disponible, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(permissions, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(jours, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(seances, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(ouvrirReglages, CAPPluginReturnPromise);
)
