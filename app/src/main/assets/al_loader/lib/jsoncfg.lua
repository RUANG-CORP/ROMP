-- Copyright (c) AL Loader (Alyn Lua Loader), 2026
-- Built-in library: jsoncfg
-- Lua package is distributed under the MIT license

--[[
  JSON settings files, the counterpart of inicfg.

    local jsoncfg = require 'jsoncfg'
    local settings = jsoncfg.load({ enabled = true, keys = {} }, 'myscript')
    settings.enabled = false
    jsoncfg.save(settings, 'myscript')

  load(default, name, extension)  -> table
    Reads the file and fills in every key the file lacks from `default`
    (recursively). A missing, empty or unreadable file gives a copy of
    `default`. Never returns the `default` table itself.
  save(data, name, extension)     -> true | false, error
    Writes `data` as JSON, creating the folder if needed.

  `name` defaults to the script's file name (myscript.lua -> myscript.lua.json,
  the name scripts already have on disk), `extension` to '.json'.
  A bare name lives in mods/AL Loader/config/. A name with a folder in it is a
  path: relative ones are taken from mods/AL Loader/ on every platform (the PC
  game runs from the game folder, so the process directory is not used).
  Backslashes are accepted and turned into '/'.
]]

local jsoncfg = {}

local DEFAULT_EXTENSION = '.json'

local function copy(value, seen)
  if type(value) ~= 'table' then
    return value
  end
  seen = seen or {}
  if seen[value] then
    return seen[value]
  end
  local result = {}
  seen[value] = result
  for k, v in pairs(value) do
    result[copy(k, seen)] = copy(v, seen)
  end
  return setmetatable(result, getmetatable(value))
end

-- Adds to `target` every key of `defaults` it does not have; nested tables
-- present on both sides are completed the same way.
local function complete(target, defaults)
  for k, v in pairs(defaults) do
    local current = target[k]
    if current == nil then
      target[k] = copy(v)
    elseif type(current) == 'table' and type(v) == 'table' then
      complete(current, v)
    end
  end
end

local function is_absolute(path)
  return path:sub(1, 1) == '/' or path:match('^%a:/') ~= nil
end

-- The file a name refers to, and the other spellings load also accepts.
local function candidates(name, extension)
  extension = extension or DEFAULT_EXTENSION
  name = (name or (script.this.filename .. extension)):gsub('\\', '/')
  local root = getWorkingDirectory():gsub('\\', '/')

  local with_extension = name
  if extension ~= '' and name:sub(-#extension):lower() ~= extension:lower() then
    with_extension = name .. extension
  end

  if name:find('/', 1, true) then
    local path = is_absolute(with_extension) and with_extension or (root .. '/' .. with_extension)
    local plain = is_absolute(name) and name or (root .. '/' .. name)
    return path, { path, plain }
  end
  local dir = root .. '/config/'
  return dir .. with_extension, { dir .. with_extension, dir .. name }
end

local function read_file(path)
  local file = io.open(path, 'rb')
  if not file then
    return nil
  end
  local contents = file:read('*a')
  file:close()
  return contents
end

function jsoncfg.load(default, name, extension)
  default = default or {}
  local _, paths = candidates(name, extension)
  for _, path in ipairs(paths) do
    local contents = read_file(path)
    if contents then
      if contents:match('^%s*$') then
        break
      end
      local ok, data = pcall(decodeJson, contents)
      if ok and type(data) == 'table' then
        complete(data, default)
        return data
      end
      print('jsoncfg: ' .. path .. ' is not valid JSON (' .. tostring(data) .. '), using the defaults')
      break
    end
  end
  return copy(default)
end

function jsoncfg.save(data, name, extension)
  assert(type(data) == 'table', 'jsoncfg.save: data must be a table')
  local path = candidates(name, extension)
  local dir = path:match('^(.*)/[^/]*$')
  if dir and dir ~= '' and not doesDirectoryExist(dir) then
    createDirectory(dir)
  end

  local ok, encoded = pcall(encodeJson, data, false)
  if not ok then
    print('jsoncfg: cannot encode ' .. path .. ': ' .. tostring(encoded))
    return false, encoded
  end
  local file, err = io.open(path, 'wb')
  if not file then
    print('jsoncfg: cannot write ' .. path .. ': ' .. tostring(err))
    return false, err
  end
  file:write(encoded)
  file:close()
  return true
end

return jsoncfg
