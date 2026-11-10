-- Info HUD for AL Loader.
-- A small line in the corner with FPS, ping, position, interior and the time.
--   /infohud   show or hide it (remembered in mods/AL Loader/config/info_hud.json)
script_name("Info HUD")
script_author("ReSA")
script_version("1.0")
script_description("FPS, ping, position and time in a corner of the screen. /infohud")

local jsoncfg = require "jsoncfg"

local cfg = jsoncfg.load({ visible = true }, "info_hud")
local font
-- localClock(): wall seconds. os.clock() is process CPU time and runs faster
-- than real time while the game's threads work.
local frames, fps, fps_since = 0, 0, localClock()

local function local_player_id()
  local ok, id = sampGetPlayerIdByCharHandle(PLAYER_PED)
  return ok and id or nil
end

function onD3DPresent()
  frames = frames + 1
  local now = localClock()
  if now - fps_since >= 1 then
    fps = math.floor(frames / (now - fps_since) + 0.5)
    frames, fps_since = 0, now
  end
  if not cfg.visible or not font or not doesCharExist(PLAYER_PED) then
    return
  end

  local x, y, z = getCharCoordinates(PLAYER_PED)
  local parts = { string.format("FPS %d", fps) }
  local id = isSampAvailable() and local_player_id()
  if id then
    parts[#parts + 1] = string.format("Ping %d", sampGetPlayerPing(id))
    parts[#parts + 1] = string.format("ID %d", id)
  end
  parts[#parts + 1] = string.format("%.1f %.1f %.1f", x, y, z)
  local interior = getActiveInterior()
  if interior ~= 0 then
    parts[#parts + 1] = "Int " .. interior
  end
  parts[#parts + 1] = os.date("%H:%M")

  local text = table.concat(parts, "  |  ")
  local sw, sh = getScreenResolution()
  local width = renderGetFontDrawTextLength(font, text)
  local px, py = sw - width - 16 * AL_DPI_SCALE, sh - 26 * AL_DPI_SCALE
  renderDrawBox(px - 8, py - 3, width + 16, 22 * AL_DPI_SCALE, 0x88101418)
  renderFontDrawText(font, text, px, py, 0xFFE8F0FF)
end

function main()
  while not doesCharExist(PLAYER_PED) do
    wait(100)
  end
  font = renderCreateFont("Arial", 10 * AL_DPI_SCALE, 5)
  while not isSampAvailable() do
    wait(100)
  end
  sampRegisterChatCommand("infohud", function()
    cfg.visible = not cfg.visible
    jsoncfg.save(cfg, "info_hud")
  end)
  wait(-1)
end
