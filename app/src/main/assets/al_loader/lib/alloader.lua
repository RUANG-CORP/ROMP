-- Copyright (c) AL Loader (Alyn Lua Loader), 2026
-- Built-in library: alloader (AL Loader)
-- Lua package is distributed under the MIT license

local touch_events = {
  POP = 1,
  PUSH = 2,
  MOVE = 3
}

return {
  touch_events = touch_events,
  platform = AL_PLATFORM,
  version = AL_VERSION
}
