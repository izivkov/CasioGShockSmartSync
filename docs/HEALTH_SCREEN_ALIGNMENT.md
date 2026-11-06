# Health Screen Alignment Specification (for AI / TypeScript Web App Migration)

This document provides complete instructions and specifications for updating the TypeScript web application (or web companion app) to mirror the Android application's **Health Screen** architecture, navigation tab, and inline Exercise Profile layout.

---

## 1. Overview of Architectural Changes

In previous versions, the step counter card was embedded directly inside the **Time** screen. The Health features are now decoupled into a dedicated **Health** screen:

1. **Dedicated Route**: A new `/health` (or `Health`) navigation tab.
2. **Heart Icon Navigation**: Rendered with a heart icon (e.g. `lucide-react` `Heart`, `@mui/icons-material/Favorite`, or SVG heart).
3. **Hardware-Gated Tab**: Displayed in the navigation bar **only** if `watchInfo.hasStepCounter === true` (or mock step mode is enabled).
4. **Clean Time Screen**: The Step Counter card and Exercise Profile settings are completely removed from the Time screen.
5. **Inline Exercise Profile**: The Exercise Profile is no longer a popup dialog; it is a full, inline card directly on the Health screen.
6. **No Step Goal Slider**: The slider on the step progress ring has been removed in favor of explicit input in the Exercise Profile card.

---

## 2. Navigation & Route Configuration

### Navigation Item Specification

| Property | Value / Specification |
| :--- | :--- |
| **Route Name** | `"Health"` / `/health` |
| **Tab Label** | `"Health"` (Localized: EN `"Health"`, ES `"Salud"`, FR `"Santé"`, DE `"Gesundheit"`, JA `"ヘルスケア"`, ZH `"健康"`) |
| **Icon** | Heart Icon (`Icons.Filled.Favorite` / `lucide:heart` / `fa-heart`) |
| **Visibility Condition** | `watchInfo.hasStepCounter === true \|\| mockMode === true` |
| **Tab Position** | Inserted immediately after `Time` tab (`Time` → `Health` → `Alarms` → `Events` → `Actions` → `Settings`) |

### Example TypeScript Navigation Definition

```typescript
export interface NavItem {
  id: string;
  label: string;
  route: string;
  icon: string; // e.g., 'heart'
  isSupported: (watchInfo: WatchInfo) => boolean;
}

export const navItems: NavItem[] = [
  { id: 'time', label: 'Time', route: '/time', icon: 'clock', isSupported: () => true },
  { 
    id: 'health', 
    label: 'Health', 
    route: '/health', 
    icon: 'heart', 
    isSupported: (watchInfo) => watchInfo.hasStepCounter || watchInfo.hasStepCounterMock 
  },
  { id: 'alarms', label: 'Alarms', route: '/alarms', icon: 'alarm', isSupported: (watchInfo) => watchInfo.alarmCount > 0 },
  // ...other items
];
```

---

## 3. Health Screen Component Layout

The Health screen uses a scrollable vertical column containing two main cards:

```
+-------------------------------------------------+
| HEALTH (Header / Title)                         |
+-------------------------------------------------+
| [ Card 1: Activity / Steps Dashboard ]          |
|   Steps | Clear History | [ View: Today v ]     |
|                                                 |
|               ( 4,500 )                         |
|            ---  of 10,000 steps ---             |
|             3.4 km  |  248 kcal                 |
|                                                 |
+-------------------------------------------------+
| [ Card 2: Exercise Profile ]                     |
|   Exercise Profile                              |
|   Set step goal, height, and weight for watch   |
|                                                 |
|   Step Goal                                     |
|   [ 10000                            ]          |
|                                                 |
|   Height (cm / in)      Weight (kg / lb)        |
|   [ 175              ]  [ 70             ]      |
|                                                 |
|   [ SAVE PROFILE TO WATCH ]                     |
+-------------------------------------------------+
```

---

## 4. Component 1: Step Counter & Activity Card (`StepCounterCard.tsx`)

### Elements & Features:
1. **Header Row**:
   - Title: `"Steps"`
   - Action: `"Clear History"` button (triggers hardware hardware buffer wipe).
   - View Selector Dropdown: `TODAY` | `HOURLY` | `DAILY`.
2. **View Modes**:
   - **`TODAY` (Default)**:
     - Prominent, centered step progress arc (half-circle gauge / ring).
     - Center text: Current Steps (large font, e.g. `4500`).
     - Subtitle: `"of {stepGoal} steps"`.
     - Metrics Row: Distance (e.g. `3.4 km` or `2.1 mi`) & Estimated Calories (e.g. `248 kcal`).
     - **Note**: Do NOT include a slider here.
   - **`HOURLY`**: Bar chart showing step counts for the last 10 hours with labels (`12a`, `1a`, etc.).
   - **`DAILY`**: Bar chart showing step counts for the last 7 days with weekday labels (`Mo`, `Tu`, etc.).

---

## 5. Component 2: Inline Exercise Profile Card (`ExerciseProfileCard.tsx`)

### Elements & Features:
1. **Header**: Title `"Exercise Profile"` and subtitle `"Set step goal, height, and weight for your watch."`.
2. **Inputs**:
   - **Step Goal**: Number input (`1000` to `30000` steps).
   - **Height**: Number input displaying units based on locale (`Height (cm)` or `Height (in)`).
   - **Weight**: Number input displaying units based on locale (`Weight (kg)` or `Weight (lb)`).
3. **Save Action**: `"Save Profile to Watch"` button with loading spinner and toast/snackbar feedback upon completion.

---

## 6. TypeScript Data Models & Calculations

### Interfaces

```typescript
export enum StepDataOption {
  TODAY = 'TODAY',
  HOURLY = 'HOURLY',
  DAILY = 'DAILY'
}

export interface ExerciseProfile {
  stepGoal: number; // 1,000 - 30,000
  height: number;   // stored in cm internally
  weight: number;   // stored in kg internally
}

export interface StepCounterData {
  currentDaySteps?: number;
  distanceMeters?: number;
  totalDistanceMeters?: number;
  hourlySteps: number[]; // 144 intervals or last 10 hours
  dailyHistory: number[];
}

export interface HealthState {
  stepCounterData: StepCounterData;
  selectedStepDataOption: StepDataOption;
  stepGoal: number;
  weight: number; // kg
  height: number; // cm
  calories: number;
  distanceKm: number;
  isLoading: boolean;
  error?: string;
}
```

### Unit Conversion & Metric Calculation Logic

```typescript
const IMPERIAL_COUNTRIES = new Set(['US', 'LR', 'MM', 'CA']);

export function isImperialLocale(countryCode?: string): boolean {
  const code = (countryCode || navigator.language.split('-')[1] || 'US').toUpperCase();
  return IMPERIAL_COUNTRIES.has(code);
}

// Height conversion
export function cmToInches(cm: number): number {
  return Math.round(cm / 2.54);
}

export function inchesToCm(inches: number): number {
  return Math.round(inches * 2.54);
}

// Weight conversion
export function kgToPounds(kg: number): number {
  return Math.round(kg * 2.20462);
}

export function poundsToKg(pounds: number): number {
  return Math.round(pounds / 2.20462);
}

// Distance & Calorie Calculations
export function resolveDistanceKm(stepData: StepCounterData, strideMeters = 0.73): number {
  const steps = stepData.currentDaySteps ?? 0;
  const rawMeters = stepData.distanceMeters;

  if (rawMeters && rawMeters > 0) {
    const minReasonable = steps * 0.4;
    const maxReasonable = steps * 0.85;
    if (rawMeters >= minReasonable && rawMeters <= maxReasonable) {
      return rawMeters / 1000;
    }
  }

  return (steps * strideMeters) / 1000;
}

export function calculateStepMetrics(stepData: StepCounterData, weightKg: number): { distanceKm: number; calories: number } {
  const distanceKm = resolveDistanceKm(stepData);
  const calories = distanceKm * weightKg * 1.036;
  return { distanceKm, calories };
}
```

---

## 7. Instructions for Web App AI / Developer

When updating your React / Vue / Svelte / Angular TypeScript web app:

1. **Add Route**: Define `/health` route and point it to `<HealthPage />` or `<HealthScreen />`.
2. **Update Nav Bar**: Render the Health tab with a Heart icon conditionally when `watchInfo.hasStepCounter` is true.
3. **Clean Up Time Page**: Remove step counter widgets, sliders, and profile dialogs from `<TimePage />`.
4. **Create Health Page**: Assemble `<HealthPage />` with `<StepCounterCard />` and `<ExerciseProfileCard />` inside a scrollable layout.
5. **Decouple Store**: Create `useHealthStore` / `HealthContext` to isolate step data polling and exercise profile I/O from the Time screen store.
