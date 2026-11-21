plugins {
    alias(libs.plugins.sunup.kmp.compose)
}

// Sunup's look: colors, type, spacing and shared components such as the chunky 3D button.

// Nunito, the mockups' font (composeResources/font/nunito.ttf: Google Fonts' variable file,
// every weight in one; licence in NUNITO_OFL.txt).
compose.resources {
    packageOfResClass = "dev.victorc.sunup.core.designsystem.resources"
    publicResClass = false
}
