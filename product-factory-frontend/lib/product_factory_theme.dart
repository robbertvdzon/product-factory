import 'package:flutter/material.dart';

abstract final class ProductFactoryColors {
  static const ink = Color(0xff102a2c);
  static const inkSoft = Color(0xff193335);
  static const sidebar = Color(0xff102a2c);
  static const sidebarSelected = Color(0xff214345);
  static const mint = Color(0xff22c99b);
  static const primary = Color(0xff3f8f77);
  static const background = Color(0xfff3f5f1);
  static const surface = Color(0xfffffefd);
  static const outline = Color(0xffdbe3dd);
  static const muted = Color(0xff6d807e);
  static const warning = Color(0xfff6a455);
}

ThemeData productFactoryTheme() {
  final scheme =
      ColorScheme.fromSeed(
        seedColor: ProductFactoryColors.primary,
        brightness: Brightness.light,
        surface: ProductFactoryColors.surface,
      ).copyWith(
        primary: ProductFactoryColors.primary,
        onPrimary: Colors.white,
        secondary: ProductFactoryColors.mint,
        onSurface: ProductFactoryColors.inkSoft,
        surface: ProductFactoryColors.surface,
        outline: ProductFactoryColors.outline,
        surfaceContainerHighest: const Color(0xffedf2ed),
      );
  final base = ThemeData(colorScheme: scheme, useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: ProductFactoryColors.background,
    textTheme: base.textTheme.copyWith(
      displaySmall: base.textTheme.displaySmall?.copyWith(
        color: ProductFactoryColors.ink,
        fontWeight: FontWeight.w800,
        letterSpacing: -1.4,
        height: 1.05,
      ),
      headlineMedium: base.textTheme.headlineMedium?.copyWith(
        color: ProductFactoryColors.ink,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.7,
      ),
      headlineSmall: base.textTheme.headlineSmall?.copyWith(
        color: ProductFactoryColors.ink,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.4,
      ),
      titleLarge: base.textTheme.titleLarge?.copyWith(
        color: ProductFactoryColors.ink,
        fontWeight: FontWeight.w700,
      ),
      titleMedium: base.textTheme.titleMedium?.copyWith(
        color: ProductFactoryColors.ink,
        fontWeight: FontWeight.w700,
      ),
      bodyMedium: base.textTheme.bodyMedium?.copyWith(
        color: ProductFactoryColors.inkSoft,
        height: 1.5,
      ),
      bodySmall: base.textTheme.bodySmall?.copyWith(
        color: ProductFactoryColors.muted,
        height: 1.45,
      ),
      labelLarge: base.textTheme.labelLarge?.copyWith(
        fontWeight: FontWeight.w700,
      ),
    ),
    cardTheme: const CardThemeData(
      color: ProductFactoryColors.surface,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(16)),
        side: BorderSide(color: ProductFactoryColors.outline),
      ),
    ),
    dividerTheme: const DividerThemeData(
      color: ProductFactoryColors.outline,
      space: 1,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 46),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 46),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        side: const BorderSide(color: ProductFactoryColors.outline),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: ProductFactoryColors.primary,
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: ProductFactoryColors.outline),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: ProductFactoryColors.outline),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(
          color: ProductFactoryColors.primary,
          width: 2,
        ),
      ),
    ),
    chipTheme: base.chipTheme.copyWith(
      backgroundColor: const Color(0xffedf5ef),
      side: BorderSide.none,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      labelStyle: const TextStyle(
        color: ProductFactoryColors.inkSoft,
        fontWeight: FontWeight.w600,
      ),
    ),
    dialogTheme: const DialogThemeData(
      backgroundColor: ProductFactoryColors.background,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(22)),
      ),
    ),
  );
}
