"use client";

import { Box, ThemeProvider, createTheme, useMediaQuery, useTheme } from "@mui/material";
import {ReactNode, Suspense, useMemo} from "react";
import CssBaseline from '@mui/material/CssBaseline';
import { getTheme } from "@/app/style/theme";
import "@/app/style/global.css"
import SuspenseLoad from "./_components/SuspenseLoad";
import {LanguageProvider, useLanguage} from "@/contexts/LanguageContext";

function ThemeWrapper({ children }: { children: ReactNode }) {
  // Get system preference for dark/light mode
  const prefersDarkMode = useMediaQuery("(prefers-color-scheme: dark)");
  const { direction } = useLanguage();

  // Implement custom theme based on system preference and language direction
  const theme = useMemo(
    () => createTheme({
        ...getTheme(prefersDarkMode ? "dark" : "light"),
        direction: direction
    }),
    [prefersDarkMode, direction],
  );

  return (
      <ThemeProvider theme={theme}>
        <Box sx={{backgroundColor: "background.default"}} dir={direction}>
          <CssBaseline />
          {children}
        </Box>
      </ThemeProvider>
  );
}

export default function Providers({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<SuspenseLoad />}>
      <LanguageProvider>
          <ThemeWrapper>
            {children}
          </ThemeWrapper>
      </LanguageProvider>
    </Suspense>
  );
}

// Handle breakpoints for desktop vs. tablets vs. mobile displays
export const useBreakpoint = () => {
  const theme = useTheme();

  const isDesktop = useMediaQuery(theme.breakpoints.up("lg"));
  const isTablet = useMediaQuery(theme.breakpoints.up("md"));
  const isSmallTablet = useMediaQuery(theme.breakpoints.up("sm"))

  const size = isDesktop ? "desktop" : isTablet ? "tablet" : isSmallTablet ? "smallTablet" : "mobile"

  return {
    size,
    isMobile: size === "mobile",
    isSmallTablet: size === "smallTablet",
    isTablet: size === "tablet",
    isDesktop: size === "desktop",
  };
}
