# Apache POI (core module; only used by the desktop console for Excel import)
# references optional libs (OSGi, log4j extras, Batik/SVG, w3c svg) that don't
# exist on Android. POI code paths are never executed in the teacher app, so
# these missing-class warnings are safe to silence.
-dontwarn org.osgi.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.poi.**
-dontwarn org.w3c.dom.svg.**
