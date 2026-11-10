-- InfoWindow for AL Loader.
-- Outlined lines of text on the game screen with your FPS, ping, ID and
-- position. Pick the lines, the text size and the place in a menu, or drag the
-- text itself while the menu is open. Rewritten for AL Loader from the
-- InfoWindow overlay by Cyabo; its menu and command are kept.
-- Saved in mods/AL Loader/config/info_window.json.
--   /adjinfow   open or close the menu
script_name("InfoWindow")
script_author("Cyabo, ReSA")
script_version("1.1")
script_description("FPS, ping, your ID and position as outlined text you can move and pick lines for. /adjinfow")

local imgui = require "mimgui"
local jsoncfg = require "jsoncfg"

-- The place is kept as a fraction of the screen, so it stays put when the
-- resolution changes. The default sits above the radar and below the phone
-- layout's left-edge buttons.
local DEFAULTS = {
  visible = true,
  fps = true,
  ping = true,
  id = true,
  pos_x = true,
  pos_y = true,
  pos_z = false,
  scale = 1.0,
  x = 0.015,
  y = 0.52,
}
local LINES = {
  { key = "fps", label = "FPS" },
  { key = "ping", label = "Ping" },
  { key = "id", label = "Player ID" },
  { key = "pos_x", label = "Position X" },
  { key = "pos_y", label = "Position Y" },
  { key = "pos_z", label = "Position Z" },
}

local cfg = jsoncfg.load(DEFAULTS, "info_window")
local visible = imgui.new.bool(cfg.visible)
local menu = imgui.new.bool(false)
local show = {}
for _, line in ipairs(LINES) do
  show[line.key] = imgui.new.bool(cfg[line.key])
end
local scale = imgui.new.float(tonumber(cfg.scale) or 1.0)
local pos_x = imgui.new.float(0)
local pos_y = imgui.new.float(0)
local stepped_aside = false -- the player runs an InfoWindow of their own (main)
local place_from_cfg = true -- turn cfg.x/cfg.y into pixels on the next frame
local move_window = true -- put the window at pos_x/pos_y on the next frame

-- localClock(): wall seconds. os.clock() is process CPU time and runs faster
-- than real time while the game's threads work.
local frames, fps, fps_since = 0, 0, localClock()

local function save()
  local sw, sh = getScreenResolution()
  cfg.visible = visible[0]
  for _, line in ipairs(LINES) do
    cfg[line.key] = show[line.key][0]
  end
  cfg.scale = scale[0]
  cfg.x = pos_x[0] / sw
  cfg.y = pos_y[0] / sh
  jsoncfg.save(cfg, "info_window")
end

local function reset()
  visible[0] = DEFAULTS.visible
  for _, line in ipairs(LINES) do
    show[line.key][0] = DEFAULTS[line.key]
  end
  scale[0] = DEFAULTS.scale
  cfg.x, cfg.y = DEFAULTS.x, DEFAULTS.y
  place_from_cfg = true
end

local function lines_text()
  local out = {}
  if show.fps[0] then
    out[#out + 1] = string.format("FPS: %d", fps)
  end
  local ok, id = false, -1
  if isSampAvailable() then
    ok, id = sampGetPlayerIdByCharHandle(PLAYER_PED)
  end
  if show.ping[0] then
    out[#out + 1] = ok and string.format("Ping: %d", sampGetPlayerPing(id)) or "Ping: -"
  end
  if show.id[0] then
    out[#out + 1] = ok and string.format("Player ID: %d", id) or "Player ID: -"
  end
  local x, y, z = getCharCoordinates(PLAYER_PED)
  if show.pos_x[0] then
    out[#out + 1] = string.format("Position X: %.2f", x)
  end
  if show.pos_y[0] then
    out[#out + 1] = string.format("Position Y: %.2f", y)
  end
  if show.pos_z[0] then
    out[#out + 1] = string.format("Position Z: %.2f", z)
  end
  return out
end

-- White text with a black outline, drawn one pixel (scaled) off each side.
local function outlined_text(text)
  local p = imgui.GetCursorScreenPos()
  local draw_list = imgui.GetWindowDrawList()
  local black = imgui.GetColorU32Vec4(imgui.ImVec4(0.0, 0.0, 0.0, 1.0))
  local white = imgui.GetColorU32Vec4(imgui.ImVec4(1.0, 1.0, 1.0, 1.0))
  local o = math.max(1, math.floor(AL_DPI_SCALE * scale[0] + 0.5))
  draw_list:AddText(imgui.ImVec2(p.x - o, p.y), black, text)
  draw_list:AddText(imgui.ImVec2(p.x + o, p.y), black, text)
  draw_list:AddText(imgui.ImVec2(p.x, p.y - o), black, text)
  draw_list:AddText(imgui.ImVec2(p.x, p.y + o), black, text)
  draw_list:AddText(p, white, text)
  imgui.Dummy(imgui.CalcTextSize(text))
end

-- The text. It takes no touches unless the menu is open; then it can be
-- dragged and shows a frame.
local overlay = imgui.OnFrame(function()
  return not stepped_aside and (visible[0] or menu[0]) and doesCharExist(PLAYER_PED)
end, function()
  local sw, sh = getScreenResolution()
  local now = localClock()
  frames = frames + 1
  if now - fps_since >= 1 then
    fps = math.floor(frames / (now - fps_since) + 0.5)
    frames, fps_since = 0, now
  end
  if place_from_cfg then
    pos_x[0], pos_y[0] = cfg.x * sw, cfg.y * sh
    place_from_cfg, move_window = false, true
  end
  if not visible[0] then
    return
  end

  local editing = menu[0]
  local flags = imgui.WindowFlags.NoTitleBar + imgui.WindowFlags.NoResize + imgui.WindowFlags.NoScrollbar
    + imgui.WindowFlags.NoCollapse + imgui.WindowFlags.NoSavedSettings + imgui.WindowFlags.AlwaysAutoResize
    + imgui.WindowFlags.NoFocusOnAppearing + imgui.WindowFlags.NoBringToFrontOnFocus
  if not editing then
    flags = flags + imgui.WindowFlags.NoInputs + imgui.WindowFlags.NoMove
    move_window = true
  end
  if move_window then
    imgui.SetNextWindowPos(imgui.ImVec2(pos_x[0], pos_y[0]), imgui.Cond.Always)
    move_window = false
  end
  local s = AL_DPI_SCALE
  imgui.PushStyleColor(imgui.Col.WindowBg, editing and imgui.ImVec4(0, 0, 0, 0.35) or imgui.ImVec4(0, 0, 0, 0))
  imgui.PushStyleColor(imgui.Col.Border, editing and imgui.ImVec4(1, 0.8, 0.2, 0.9) or imgui.ImVec4(0, 0, 0, 0))
  -- The same padding either way, so the text does not jump when the menu
  -- opens; it also leaves room for the outline.
  imgui.PushStyleVarVec2(imgui.StyleVar.WindowPadding, imgui.ImVec2(4 * s, 3 * s))
  imgui.PushStyleVarVec2(imgui.StyleVar.ItemSpacing, imgui.ImVec2(0, 2 * s * scale[0]))
  if imgui.Begin("##InfoWindowOverlay", nil, flags) then
    imgui.SetWindowFontScale(scale[0])
    local lines = lines_text()
    if #lines == 0 and editing then
      lines[1] = "(no lines)"
    end
    for _, text in ipairs(lines) do
      outlined_text(text)
    end
    if editing then
      local p = imgui.GetWindowPos()
      pos_x[0], pos_y[0] = p.x, p.y
    end
  end
  imgui.End()
  imgui.PopStyleVar(2)
  imgui.PopStyleColor(2)
end)
overlay.HideCursor = true

-- The menu (/adjinfow).
imgui.OnFrame(function() return not stepped_aside and menu[0] end, function()
  if not menu[0] then
    return -- closed since the condition was polled
  end
  local s = AL_DPI_SCALE
  local sw, sh = getScreenResolution()
  imgui.SetNextWindowPos(imgui.ImVec2(sw * 0.5, sh * 0.5), imgui.Cond.FirstUseEver, imgui.ImVec2(0.5, 0.5))
  imgui.SetNextWindowSize(imgui.ImVec2(340 * s, 0), imgui.Cond.Always)
  if imgui.Begin("InfoWindow", menu, imgui.WindowFlags.NoCollapse + imgui.WindowFlags.NoResize) then
    imgui.Checkbox("Show InfoWindow", visible)
    imgui.Separator()
    imgui.Text("Lines")
    for i, line in ipairs(LINES) do
      if i % 2 == 0 then
        imgui.SameLine(170 * s)
      end
      imgui.Checkbox(line.label, show[line.key])
    end
    imgui.Separator()
    imgui.PushItemWidth(-90 * s)
    imgui.SliderFloat("Text size", scale, 0.6, 2.5, "%.1f")
    if imgui.SliderFloat("Position X", pos_x, 0, sw, "%.0f") then
      move_window = true
    end
    if imgui.SliderFloat("Position Y", pos_y, 0, sh, "%.0f") then
      move_window = true
    end
    imgui.PopItemWidth()
    imgui.TextDisabled("You can also drag the text while this menu is open.")
    imgui.Separator()
    local button = imgui.ImVec2(120 * s, 35 * s)
    if imgui.Button("Reset defaults", button) then
      reset()
    end
    imgui.SameLine()
    if imgui.Button("Close", button) then
      menu[0] = false
    end
  end
  imgui.End()
  -- Closed by its button or its X.
  if not menu[0] then
    save()
  end
end)

function onScriptTerminate(script)
  if script == thisScript() and menu[0] then
    save()
  end
end

-- This file is the copy the launcher installs (Recommended scripts). A player
-- who also runs an InfoWindow of their own - Cyabo's original, or a copy under
-- another name - keeps theirs and this one stays off: two copies drew the same
-- lines over each other, and /adjinfow reached only one of them.
local RECOMMENDED_FILE = "info_window.lua"

local function squashed(text)
  local out = (text or ""):lower():gsub("[%s_%-%.]", "")
  return out
end

local function players_own_info_window()
  local me = thisScript()
  if (me.filename or ""):lower() ~= RECOMMENDED_FILE then
    return nil -- this is the player's own copy: it never steps aside
  end
  for _, s in ipairs(script.list()) do
    if s.id ~= me.id and not s.dead then
      if squashed(s.name) == "infowindow" or squashed(s.filename):find("^infowindow") then
        return s
      end
    end
  end
  return nil
end

local function step_aside(reason)
  stepped_aside = true
  menu[0] = false
  print("[InfoWindow] " .. reason .. "; the recommended copy stays off.")
  thisScript():unload()
end

function main()
  local other = players_own_info_window()
  if other then
    step_aside("your own InfoWindow is running (" .. tostring(other.filename) .. ")")
    return
  end
  while not isSampAvailable() do
    wait(100)
  end
  local registered = sampRegisterChatCommand("adjinfow", function()
    menu[0] = not menu[0]
    if not menu[0] then
      save()
    end
  end)
  if not registered and (thisScript().filename or ""):lower() == RECOMMENDED_FILE then
    step_aside("another script already has /adjinfow")
    return
  end
  sampAddChatMessage("[InfoWindow] {FFFFFF}loaded! Use {FFFF00}/adjinfow {FFFFFF}to adjust it.", 0x2ECC71)
  wait(-1)
end
