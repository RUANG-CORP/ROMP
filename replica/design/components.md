# Component Specifications: ROMP / Valleta

Rebuilt clean-room design system components for Android Launcher & In-Game CEF Overlay.

## Primitives & Components

### 1. ServerCard
- **Variants:** Favorite (with red Delete button), Public Directory (no Delete button)
- **Sizes:** Height wrap_content (~90dp), full width
- **States:** Default (slate-800), Querying (grey dot, ping "..."), Online (green dot #10B981, live ping), Offline (red dot #EF4444, "Offline")
- **Tokens:** Background `surface`, Border `border`, Text `text`, Subtext `text-muted`, Status dot 10x10dp circle
- **A11y:** Focusable card with accessible text and clear button targets (>36dp)
- **Used on:** S01 (Favorit), S02 (Internet)

### 2. TabButton
- **Variants:** Active tab, Inactive tab
- **Sizes:** Height 48dp, equal weight (0dp, weight 1)
- **States:** Active (Text `#38BDF8` bold), Inactive (Text `#94A3B8` regular), Pressed (selectableItemBackground ripple)
- **Tokens:** Background `#1E293B`, Active text `#38BDF8`, Inactive text `#94A3B8`
- **Used on:** Header Navigation (S01, S02, S03)

### 3. SearchBar
- **Variants:** Standard server filter input
- **Sizes:** Height 40dp, full width, paddingStart 12dp
- **States:** Normal, Focused (border accent), Filled, Cleared
- **Tokens:** Background `#1E293B`, Text `#FFFFFF`, Hint `#64748B`, Radius `radius-sm`
- **Behavior:** Realtime text watcher triggers `adapter.filter(query)` without lagging UI thread
- **Used on:** S01, S02

### 4. ActionButton
- **Variants:** Primary Connect (`#0275B8`), Danger Delete (`#334155` with red text `#EF4444`), Success Download (`#10B981`), Neutral Secondary
- **Sizes:** Small (36dp height), Normal (44dp height), Large (48dp height)
- **States:** Default, Pressed/Ripple, Disabled (`#475569`, text `#94A3B8`), Loading ("Mengunduh...")
- **Tokens:** Radius `radius-sm` (4dp) to `radius-md` (8dp), Text bold 11sp-14sp
- **Used on:** All screens

### 5. FormInput (EditText & Spinner)
- **Variants:** Text single-line, Number port, Dropdown selector, URL CDN input
- **Sizes:** Height 44dp - 48dp, padding 10dp - 12dp
- **States:** Unfocused, Focused, Error (red outline & error popup)
- **Tokens:** Background `#1E293B`, Text `#FFFFFF`, Hint `#64748B`, Error text `#EF4444`
- **Used on:** S03 (Settings), S04 (Add Server Modal)

### 6. CacheStatusBadge
- **Variants:** Complete verified (Emerald), Missing assets (Amber)
- **States:** Verified (Green text `#10B981`), Incomplete (Amber text `#F59E0B`)
- **Tokens:** Background `#1E293B`, Padding 12dp, Text 12sp
- **Used on:** S03 (Settings)

### 7. In-Game CEF HUD Overlay
- **Variants:** Health bar, Armor bar, Cash counter, Speedometer dial
- **States:** Visible, updating on game tick via JS events, transparent background
- **Tokens:** Health `#EF4444`, Armor `#38BDF8`, Cash `#10B981`, Speedometer `#0284C7`
- **Used on:** S05 (In-Game)

