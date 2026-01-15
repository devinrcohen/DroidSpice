# DroidSpice

DroidSpice is an Android application that embeds the **ngspice** simulation engine via JNI to provide interactive SPICE-based circuit analysis on Android devices.

The current focus of the project is establishing a clean, reusable Android ↔ C/C++ ↔ ngspice integration layer, with a functional user interface for editing netlists and viewing simulation results.

This project is intended as both:
- A practical circuit analysis tool
- A reference implementation for embedding ngspice in mobile applications

---

## Current Capabilities

### Simulation Types
- **Operating Point (OP) analysis**
  - DC node voltages
  - Device currents
- **AC analysis**
  - Frequency-domain magnitude and phase data
  - Complex vector handling

### Engine Integration
- Native ngspice shared library (`libngspice.so`)
- JNI bridge with callback-based data collection
- Unified vector access layer for OP and AC analyses
- Thread-safe handling of ngspice callbacks

### User Interface
- Editable SPICE netlists
- Parameterized component values
- Plotting support for AC results
- Modular fragment-based UI architecture

---

## Not Yet Implemented

The following features are **planned but not yet available**:

- **Transient analysis (`.tran`)**
- **DC sweep analysis (`.dc`)**
- Monte Carlo and statistical analyses
- Time-domain plotting
- Expanded device libraries and example circuits
- Save/load projects
- Export plots and raw data

These omissions are intentional while the core engine and data plumbing are stabilized.

---

## Build Notes

### Requirements
- Android Studio (recent version recommended)
- Android NDK
- CMake
- Prebuilt `libngspice.so` for the target ABI(s)

### Native Layout (simplified)
