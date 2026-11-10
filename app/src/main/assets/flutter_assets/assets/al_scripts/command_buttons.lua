-- Command Buttons for AL Loader.
-- Your own buttons on the game screen: one tap runs a command or says a chat
-- line. Add, rename and delete them in the menu, switch on "Move buttons" to
-- drag them where your thumb wants them, set their size and opacity.
-- Saved in mods/AL Loader/config/command_buttons.json.
--   /cmdbtn   open or close the menu (the name can be changed in the menu)
script_name("Command Buttons")
script_author("ReSA")
script_version("1.2")
script_description("Buttons on the game screen that run your commands with one tap. /cmdbtn")

local imgui = require "mimgui"
local ffi = require "ffi"
local jsoncfg = require "jsoncfg"

local cfg = jsoncfg.load({
  menu_command = "cmdbtn",
  visible = true,
  size = 64,
  opacity = 0.85,
}, "command_buttons")

-- The buttons are not in the defaults above: jsoncfg fills every missing key
-- from them, list entries included, so a deleted default button came back
-- (or turned into a second copy of the next one) on the next start.
-- Positions are fractions of the screen, so a layout survives a resolution
-- change and moves between a phone and a tablet.
if cfg.buttons == nil then
  cfg.buttons = {
    { name = "Engine", text = "/engine", x = 0.88, y = 0.38 },
    { name = "Lock", text = "/lock", x = 0.80, y = 0.38 },
  }
end

-- 1.0 put Lock at 0.88, 0.52: on the game's swap-weapons button, which a
-- script button hides under the game's controls and takes the touch from, so
-- the swap button stopped working. A Lock button still there moves beside
-- Engine.
for _, b in ipairs(cfg.buttons) do
  if b.text == "/lock" and math.abs(b.x - 0.88) < 0.01 and math.abs(b.y - 0.52) < 0.01 then
    b.x, b.y = 0.80, 0.38
    jsoncfg.save(cfg, "command_buttons")
  end
end

local menu = imgui.new.bool(false)
local moving = imgui.new.bool(false)
local visible = imgui.new.bool(cfg.visible)
local size = imgui.new.float(cfg.size)
local opacity = imgui.new.float(cfg.opacity)
local new_name = imgui.new.char[48]()
local new_text = imgui.new.char[256]()
local menu_cmd = imgui.new.char[32](cfg.menu_command)
local registered_cmd

local function save()
  cfg.visible = visible[0]
  cfg.size = size[0]
  cfg.opacity = opacity[0]
  jsoncfg.save(cfg, "command_buttons")
end

local function run(text)
  if text:sub(1, 1) == "/" then
    sampProcessChatInput(text) -- other scripts' commands first, then the server
  else
    sampSendChat(text)
  end
end

local function register_menu_command(name)
  name = name:gsub("^/", ""):gsub("%s", "")
  if name == "" then
    return false
  end
  if registered_cmd then
    sampUnregisterChatCommand(registered_cmd)
  end
  sampRegisterChatCommand(name, function()
    menu[0] = not menu[0]
  end)
  registered_cmd = name
  cfg.menu_command = name
  return true
end

imgui.OnInitialize(function()
  imgui.GetIO().IniFilename = nil
end)

-- The buttons themselves: one small borderless window each. They stay on
-- screen, so they keep the mouse on the camera on PC (HideCursor): with the
-- menu open the pointer is free and they can be clicked and moved.
local buttons_frame = imgui.OnFrame(function() return visible[0] and #cfg.buttons > 0 end, function()
  local sw, sh = getScreenResolution()
  local edge = size[0] * AL_DPI_SCALE
  local flags = imgui.WindowFlags.NoTitleBar + imgui.WindowFlags.NoResize + imgui.WindowFlags.NoScrollbar
    + imgui.WindowFlags.NoCollapse + imgui.WindowFlags.NoBackground + imgui.WindowFlags.AlwaysAutoResize
    + imgui.WindowFlags.NoSavedSettings + imgui.WindowFlags.NoFocusOnAppearing
  if not moving[0] then
    flags = flags + imgui.WindowFlags.NoMove
  end
  imgui.PushStyleVarFloat(imgui.StyleVar.Alpha, opacity[0])
  imgui.PushStyleVarVec2(imgui.StyleVar.WindowPadding, imgui.ImVec2(0, 0))
  imgui.PushStyleVarFloat(imgui.StyleVar.FrameRounding, edge * 0.25)
  for i, b in ipairs(cfg.buttons) do
    imgui.SetNextWindowPos(imgui.ImVec2(b.x * sw, b.y * sh), imgui.Cond.Always, imgui.ImVec2(0.5, 0.5))
    imgui.Begin("##cmdbtn" .. i, nil, flags)
    local tapped = imgui.Button(b.name .. "##b" .. i, imgui.ImVec2(edge * math.max(1, #b.name / 6), edge))
    if moving[0] then
      -- The button fills its window, so the window itself cannot be grabbed:
      -- a held button follows the finger instead.
      if imgui.IsItemActive() then
        local delta = imgui.GetIO().MouseDelta
        b.x = math.min(0.98, math.max(0.02, b.x + delta.x / sw))
        b.y = math.min(0.98, math.max(0.02, b.y + delta.y / sh))
      end
    elseif tapped then
      run(b.text)
    end
    imgui.End()
  end
  imgui.PopStyleVar(3)
end)
buttons_frame.HideCursor = true

-- The menu.
imgui.OnFrame(function() return menu[0] end, function()
  local sw, sh = getScreenResolution()
  local s = AL_DPI_SCALE
  -- First placement below the chat (the top third), which draws over windows.
  imgui.SetNextWindowPos(imgui.ImVec2(sw * 0.5, sh * 0.34), imgui.Cond.FirstUseEver, imgui.ImVec2(0.5, 0))
  imgui.SetNextWindowSize(imgui.ImVec2(420 * s, sh * 0.58), imgui.Cond.FirstUseEver)
  if imgui.Begin("Command Buttons", menu) then
    if imgui.Checkbox("Show buttons", visible) then
      save()
    end
    imgui.SameLine()
    if imgui.Checkbox("Move buttons", moving) and not moving[0] then
      save() -- the drag finished: keep the new spots
    end
    if imgui.SliderFloat("Size", size, 40, 120, "%.0f") then
      save()
    end
    if imgui.SliderFloat("Opacity", opacity, 0.2, 1.0, "%.2f") then
      save()
    end
    imgui.Separator()

    local remove
    for i, b in ipairs(cfg.buttons) do
      imgui.PushIDInt(i)
      imgui.Text("%s", b.name)
      imgui.SameLine()
      imgui.TextDisabled("%s", b.text)
      imgui.SameLine(imgui.GetWindowWidth() - 70 * s)
      if imgui.Button("Delete") then
        remove = i
      end
      imgui.PopID()
    end
    if remove then
      table.remove(cfg.buttons, remove)
      save()
    end

    imgui.Separator()
    imgui.InputTextWithHint("##name", "Button name", new_name, 48)
    imgui.InputTextWithHint("##text", "/command or chat line", new_text, 256)
    if imgui.Button("Add button", imgui.ImVec2(-1, 0)) then
      local name, text = ffi.string(new_name), ffi.string(new_text)
      if text ~= "" then
        -- New buttons appear in the middle; "Move buttons" puts them in place.
        cfg.buttons[#cfg.buttons + 1] = { name = name ~= "" and name or text, text = text, x = 0.5, y = 0.5 }
        new_name[0], new_text[0] = 0, 0
        save()
      end
    end

    imgui.Separator()
    imgui.Text("%s", "Menu command:")
    imgui.SameLine()
    imgui.PushItemWidth(140 * s)
    imgui.InputText("##menu_cmd", menu_cmd, 32)
    imgui.PopItemWidth()
    imgui.SameLine()
    if imgui.Button("Apply") and register_menu_command(ffi.string(menu_cmd)) then
      save()
      sampAddChatMessage("{33AAFF}[Command Buttons]{FFFFFF} menu is now /" .. cfg.menu_command, -1)
    end
  end
  imgui.End()
end)

function main()
  while not isSampAvailable() do
    wait(100)
  end
  register_menu_command(cfg.menu_command)
  wait(-1)
end
