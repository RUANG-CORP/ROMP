-- Quick Commands for AL Loader.
-- Your own buttons for the commands and lines you type all the time
-- (/engine, /lock, a greeting...). Tap one to send it; edit the list in the
-- same window. Saved in mods/AL Loader/config/quick_commands.json.
--   /qc   open or close the panel
script_name("Quick Commands")
script_author("ReSA")
script_version("1.0")
script_description("Buttons that send your favourite commands or chat lines. /qc")

local imgui = require "mimgui"
local ffi = require "ffi"
local jsoncfg = require "jsoncfg"

local cfg = jsoncfg.load({
  close_after_send = true,
  items = {
    { label = "Engine", text = "/engine" },
    { label = "Lock", text = "/lock" },
    { label = "Help", text = "/help" },
  },
}, "quick_commands")

local show = imgui.new.bool(false)
local editing = imgui.new.bool(false)
local close_after = imgui.new.bool(cfg.close_after_send)
local new_label = imgui.new.char[64]()
local new_text = imgui.new.char[256]()

local function save()
  cfg.close_after_send = close_after[0]
  jsoncfg.save(cfg, "quick_commands")
end

local function send(text)
  if text:sub(1, 1) == "/" then
    sampProcessChatInput(text) -- local commands of other scripts first, then the server
  else
    sampSendChat(text)
  end
end

imgui.OnInitialize(function()
  imgui.GetIO().IniFilename = nil
end)

imgui.OnFrame(function() return show[0] end, function()
  local sw, sh = getScreenResolution()
  local scale = AL_DPI_SCALE
  -- First placement below the chat (the top third), which draws over windows.
  imgui.SetNextWindowPos(imgui.ImVec2(sw * 0.5, sh * 0.34), imgui.Cond.FirstUseEver, imgui.ImVec2(0.5, 0))
  imgui.SetNextWindowSize(imgui.ImVec2(300 * scale, 0), imgui.Cond.FirstUseEver)
  imgui.Begin("Quick Commands", show, imgui.WindowFlags.AlwaysAutoResize)

  local remove
  for i, item in ipairs(cfg.items) do
    imgui.PushIDInt(i)
    if imgui.Button(item.label, imgui.ImVec2(editing[0] and 200 * scale or 260 * scale, 36 * scale)) then
      send(item.text)
      if close_after[0] then
        show[0] = false
      end
    end
    if editing[0] then
      imgui.SameLine()
      if imgui.Button("X", imgui.ImVec2(52 * scale, 36 * scale)) then
        remove = i
      end
      imgui.TextDisabled("%s", item.text)
    end
    imgui.PopID()
  end
  if remove then
    table.remove(cfg.items, remove)
    save()
  end

  imgui.Separator()
  imgui.Checkbox("Edit", editing)
  if editing[0] then
    imgui.InputTextWithHint("##label", "Button name", new_label, 64)
    imgui.InputTextWithHint("##text", "/command or chat line", new_text, 256)
    if imgui.Button("Add", imgui.ImVec2(-1, 0)) then
      local label, text = ffi.string(new_label), ffi.string(new_text)
      if text ~= "" then
        cfg.items[#cfg.items + 1] = { label = label ~= "" and label or text, text = text }
        new_label[0], new_text[0] = 0, 0
        save()
      end
    end
    if imgui.Checkbox("Close after sending", close_after) then
      save()
    end
  end
  imgui.End()
end)

function main()
  while not isSampAvailable() do
    wait(100)
  end
  sampRegisterChatCommand("qc", function()
    show[0] = not show[0]
  end)
  wait(-1)
end
