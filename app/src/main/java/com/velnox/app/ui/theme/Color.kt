package com.velnox.app.ui.theme

import androidx.compose.ui.graphics.Color

// Velnox Design System v2.2 colour tokens (.ai/VELNOX_DESIGN_THEME.md §2).
// Keep these as top-level vals: they are read from composables without
// a MaterialTheme extension scope.

// §2.1 Global surfaces and text
val VelnoxBackground = Color(0xFFF8FAFC)
val VelnoxForeground = Color(0xFF0F172A)
val VelnoxCard = Color(0xFFFFFFFF)
val VelnoxPopover = Color(0xFFFFFFFF)

// §2.2 Primary / Buy — dark means primary action
val VelnoxPrimary = Color(0xFF0F172A)
val VelnoxPrimaryForeground = Color(0xFFFFFFFF)
val VelnoxPrimaryHover = Color(0xFF1E293B)

// §2.3 Brand — emerald / mint, Velnox accent and positive action
val VelnoxBrand = Color(0xFF10B981)
val VelnoxBrandForeground = Color(0xFFFFFFFF)
val VelnoxBrandSoft = Color(0xFFECFDF5)
val VelnoxBrandSoftForeground = Color(0xFF047857)
val VelnoxBrandStrong = Color(0xFF059669)
val VelnoxBrandDeep = Color(0xFF0F766E)
val VelnoxBrandLight = Color(0xFF34D399)

// Secondary / muted — neutral supporting UI
val VelnoxSecondary = Color(0xFFF1F5F9)
val VelnoxSecondaryForeground = Color(0xFF0F172A)
val VelnoxMuted = Color(0xFFF1F5F9)
val VelnoxMutedForeground = Color(0xFF64748B)

// Slate scale — borders come from the slate-200 family
val VelnoxSlate50 = Color(0xFFF8FAFC)
val VelnoxSlate100 = Color(0xFFF1F5F9)
val VelnoxSlate200 = Color(0xFFE2E8F0)
val VelnoxSlate300 = Color(0xFFCBD5E1)
val VelnoxSlate400 = Color(0xFF94A3B8)
val VelnoxSlate500 = Color(0xFF64748B)
val VelnoxSlate600 = Color(0xFF475569)
val VelnoxSlate700 = Color(0xFF334155)
val VelnoxSlate900 = Color(0xFF0F172A)

val VelnoxBorder = VelnoxSlate200
val VelnoxInput = VelnoxSlate200

// Semantic — destructive / warning
val VelnoxDestructive = Color(0xFFDC2626)
val VelnoxDestructiveForeground = Color(0xFFFFFFFF)
val VelnoxDestructiveSoft = Color(0xFFFEF2F2)
val VelnoxDestructiveSoftForeground = Color(0xFFB91C1C)
val VelnoxWarning = Color(0xFFF59E0B)
val VelnoxWarningSoft = Color(0xFFFFFBEB)
val VelnoxWarningSoftForeground = Color(0xFFB45309)
val VelnoxSuccess = VelnoxBrand
val VelnoxSuccessSoft = VelnoxBrandSoft
val VelnoxSuccessSoftForeground = VelnoxBrandSoftForeground

// Focus — emerald ring (§26 checklist)
val VelnoxRing = Color(0x6610B981)

// Dark surface used by the brand mark tile
val VelnoxMarkSurface = Color(0xFF0F172A)
