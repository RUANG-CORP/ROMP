-- FreeCam for AL Loader.
-- Fly the camera on its own with on-screen buttons while your character stays
-- where it is: move it forward, back, sideways, up and down, turn and tilt it,
-- and set how fast it goes. Rewritten for AL Loader from FreeCam v2 by
-- 4trays.Team, a MonetLoader script that keeps its settings in MonetLoader's
-- own folder; the window and the controls are the same.
-- Saved in mods/AL Loader/config/freecam.json.
--   /fcam   open the camera window, or close it and give the camera back
script_name("FreeCam")
script_author("4trays.Team, ReSA")
script_version("2.1")
script_description("Fly the camera with on-screen buttons while your character stays put. /fcam")

local imgui = require "mimgui"
local faicons = require "fAwesome6"
local jsoncfg = require "jsoncfg"

local cfg = jsoncfg.load({ speed = 0.2 }, "freecam")

-- FreeCam v2 steps once per frame at MonetLoader's 60 fps. Here the steps are
-- per 1/60 s of frame time, so the camera moves the same at 120 or 240 fps.
local SPEED_STEP = 0.01
local MIN_SPEED = 0.01
local TURN_STEP = math.rad(2)
local MAX_PITCH = math.rad(89)

local window = imgui.new.bool(false)
local speed = math.max(MIN_SPEED, tonumber(cfg.speed) or 0.2)
local active = false
local cam = { x = 0.0, y = 0.0, z = 0.0, heading = 0.0, pitch = 0.0 }
local glyph_ranges -- must outlive OnInitialize until the font atlas is built

local ICON = {
  up = faicons("ANGLES_UP"),
  down = faicons("ANGLES_DOWN"),
  forward = faicons("ARROW_UP"),
  back = faicons("ARROW_DOWN"),
  left = faicons("ARROW_LEFT"),
  right = faicons("ARROW_RIGHT"),
  toggle = faicons("ATOM"),
  faster = faicons("CIRCLE_PLUS"),
  slower = faicons("CIRCLE_MINUS"),
  close = faicons("XMARK"),
}

-- SA headings turn counter-clockwise, and heading 0 looks along +Y.
local function apply()
  setFixedCameraPosition(cam.x, cam.y, cam.z, 0.0, 0.0, 0.0)
  local level = math.cos(cam.pitch)
  pointCameraAtPoint(cam.x - math.sin(cam.heading) * level, cam.y + math.cos(cam.heading) * level,
    cam.z + math.sin(cam.pitch), 2)
end

-- The camera starts a metre above the character, looking where it faces.
local function start()
  local x, y, z = getCharCoordinates(PLAYER_PED)
  cam.x, cam.y, cam.z = x, y, z + 1.0
  cam.heading = math.rad(getCharHeading(PLAYER_PED))
  cam.pitch = 0.0
  active = true
  apply()
end

local function stop()
  if active then
    active = false
    setCameraBehindPlayer()
    restoreCameraJumpcut()
  end
end

local function save()
  cfg.speed = speed
  jsoncfg.save(cfg, "freecam")
end

-- Forward and sideways stay level with the ground, as in FreeCam v2.
local function move(forward, left, up, frames)
  local step = speed * frames
  local fx, fy = -math.sin(cam.heading), math.cos(cam.heading)
  cam.x = cam.x + (fx * forward - fy * left) * step
  cam.y = cam.y + (fy * forward + fx * left) * step
  cam.z = cam.z + up * step
end

local function turn(left, up, frames)
  cam.heading = cam.heading + left * TURN_STEP * frames
  cam.pitch = math.max(-MAX_PITCH, math.min(MAX_PITCH, cam.pitch + up * TURN_STEP * frames))
end

local function close_window()
  window[0] = false
  stop()
  save()
end

-- A button that acts for as long as it is held.
local function held(label, size)
  imgui.Button(label, size)
  return imgui.IsItemActive()
end

imgui.OnInitialize(function()
  imgui.GetIO().IniFilename = nil
  local config = imgui.ImFontConfig()
  config.MergeMode = true
  config.PixelSnapH = true
  local builder = imgui.ImFontGlyphRangesBuilder()
  for _, glyph in pairs(ICON) do
    builder:AddText(glyph)
  end
  glyph_ranges = imgui.ImVector_ImWchar()
  builder:BuildRanges(glyph_ranges)
  imgui.GetIO().Fonts:AddFontFromMemoryCompressedBase85TTF(faicons.get_font_data_base85("solid"),
    14 * AL_DPI_SCALE, config, glyph_ranges[0].Data)
end)

local function push_style()
  local s = AL_DPI_SCALE
  imgui.PushStyleVarVec2(imgui.StyleVar.WindowPadding, imgui.ImVec2(15 * s, 10 * s))
  imgui.PushStyleVarVec2(imgui.StyleVar.FramePadding, imgui.ImVec2(5 * s, 5 * s))
  imgui.PushStyleVarVec2(imgui.StyleVar.ItemSpacing, imgui.ImVec2(8 * s, 8 * s))
  imgui.PushStyleVarFloat(imgui.StyleVar.WindowRounding, 10 * s)
  imgui.PushStyleVarFloat(imgui.StyleVar.FrameRounding, 6 * s)
  imgui.PushStyleColor(imgui.Col.WindowBg, imgui.ImVec4(0.07, 0.07, 0.086, 0.98))
  imgui.PushStyleColor(imgui.Col.ChildBg, imgui.ImVec4(0.07, 0.07, 0.086, 0.0))
  imgui.PushStyleColor(imgui.Col.Border, imgui.ImVec4(0.275, 0.275, 0.314, 1.0))
  imgui.PushStyleColor(imgui.Col.Text, imgui.ImVec4(0.94, 0.94, 0.96, 1.0))
  imgui.PushStyleColor(imgui.Col.TitleBg, imgui.ImVec4(0.059, 0.059, 0.078, 1.0))
  imgui.PushStyleColor(imgui.Col.TitleBgActive, imgui.ImVec4(0.098, 0.098, 0.118, 1.0))
  imgui.PushStyleColor(imgui.Col.Button, imgui.ImVec4(0.176, 0.176, 0.216, 1.0))
  imgui.PushStyleColor(imgui.Col.ButtonHovered, imgui.ImVec4(0.255, 0.255, 0.294, 1.0))
  imgui.PushStyleColor(imgui.Col.ButtonActive, imgui.ImVec4(0.333, 0.333, 0.373, 1.0))
end

local function pop_style()
  imgui.PopStyleColor(9)
  imgui.PopStyleVar(5)
end

imgui.OnFrame(function() return window[0] end, function()
  local s = AL_DPI_SCALE
  local frames = imgui.GetIO().DeltaTime * 60
  local sw, sh = getScreenResolution()
  local button = imgui.ImVec2(40 * s, 28 * s)
  local section = imgui.ImVec2(150 * s, 120 * s)
  local child_flags = imgui.WindowFlags.NoScrollbar

  push_style()
  imgui.SetNextWindowPos(imgui.ImVec2(sw * 0.2, sh * 0.5), imgui.Cond.FirstUseEver)
  imgui.SetNextWindowSize(imgui.ImVec2(480 * s, 150 * s), imgui.Cond.Always)
  local flags = imgui.WindowFlags.NoCollapse + imgui.WindowFlags.NoScrollbar + imgui.WindowFlags.NoResize
  if imgui.Begin(" FreeCam by 4trays.Team", nil, flags) then
    -- Move: up/forward, left/on-off/right, down/back.
    imgui.BeginChild("move", section, false, child_flags)
    if held(ICON.up .. "##up", button) and active then move(0, 0, 1, frames) apply() end
    imgui.SameLine(0, 3 * s)
    if held(ICON.forward .. "##forward", button) and active then move(1, 0, 0, frames) apply() end
    if held(ICON.left .. "##left", button) and active then move(0, 1, 0, frames) apply() end
    imgui.SameLine(0, 3 * s)
    imgui.PushStyleColor(imgui.Col.Button, active and imgui.ImVec4(0.39, 0.59, 1.0, 1.0) or imgui.ImVec4(0.176, 0.176, 0.216, 1.0))
    if imgui.Button(ICON.toggle .. "##toggle", button) then
      if active then stop() else start() end
    end
    imgui.PopStyleColor()
    imgui.SameLine(0, 3 * s)
    if held(ICON.right .. "##right", button) and active then move(0, -1, 0, frames) apply() end
    if held(ICON.down .. "##down", button) and active then move(0, 0, -1, frames) apply() end
    imgui.SameLine(0, 3 * s)
    if held(ICON.back .. "##back", button) and active then move(-1, 0, 0, frames) apply() end
    imgui.EndChild()

    -- Look: a cross of turn and tilt buttons.
    imgui.SameLine(0, 3 * s)
    imgui.BeginChild("look", section, false, child_flags)
    imgui.Dummy(button)
    imgui.SameLine(0, 3 * s)
    if held(ICON.forward .. "##look_up", button) and active then turn(0, 1, frames) apply() end
    if held(ICON.left .. "##look_left", button) and active then turn(1, 0, frames) apply() end
    imgui.SameLine(0, 3 * s)
    imgui.Dummy(button)
    imgui.SameLine(0, 3 * s)
    if held(ICON.right .. "##look_right", button) and active then turn(-1, 0, frames) apply() end
    imgui.Dummy(button)
    imgui.SameLine(0, 3 * s)
    if held(ICON.back .. "##look_down", button) and active then turn(0, -1, frames) apply() end
    imgui.EndChild()

    -- Speed and close.
    imgui.SameLine(0, 3 * s)
    imgui.BeginChild("speed", section, false, child_flags)
    local text = string.format("%.2f", speed)
    imgui.SetCursorPosX((section.x - imgui.CalcTextSize(text).x) * 0.5)
    imgui.Text(text)
    if held(ICON.faster .. "##faster", button) then speed = speed + SPEED_STEP * frames end
    imgui.SameLine(0, 3 * s)
    imgui.Dummy(button)
    imgui.SameLine(0, 3 * s)
    if held(ICON.slower .. "##slower", button) then speed = math.max(MIN_SPEED, speed - SPEED_STEP * frames) end
    imgui.Dummy(button)
    imgui.SameLine(0, 3 * s)
    imgui.PushStyleColor(imgui.Col.Button, imgui.ImVec4(0.6, 0.18, 0.18, 1.0))
    if imgui.Button(ICON.close .. "##close", button) then close_window() end
    imgui.PopStyleColor()
    imgui.EndChild()
  end
  imgui.End()
  pop_style()
end)

function onScriptTerminate(script)
  if script == thisScript() then
    stop()
  end
end

function main()
  while not isSampAvailable() do
    wait(100)
  end
  sampAddChatMessage("[FreeCam] {FFFFFF}loaded! Use {dcbcf8}/fcam {FFFFFF}to use it.", 0xDCBCF8)
  sampRegisterChatCommand("fcam", function()
    if window[0] then
      close_window()
    else
      window[0] = true
      start()
    end
  end)
  wait(-1)
end
