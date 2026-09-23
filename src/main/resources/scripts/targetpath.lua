-- imports
import("core.project.config")
import("core.project.project")

function main (targetname)

    -- load config
    config.load()
    if not os.isfile(os.projectfile()) then
        return
    end

    -- get target
    local target = nil
    if targetname then
        target = project.target(targetname)
    end
    if not target then
        for _, t in pairs(project.targets()) do
            local default = t:get("default")
            if (default == nil or default == true) and t:get("kind") == "binary" then
                target = t
                break
            end
        end
    end

    -- denote the start of vscode information to ignore anything logging to stdout before this point
    print("__begin__")

    -- get target path
    if target then
        local targetfile = target:targetfile()
        if not path.is_absolute(targetfile) then
            targetfile = path.absolute(targetfile, os.projectdir())
        end
        print(targetfile)

        -- xmake run uses set_rundir() when configured, and otherwise the target
        -- file directory. Return that effective directory for IDE debugger launches.
        local rundir = target:rundir()
        if rundir and not path.is_absolute(rundir) then
            rundir = path.absolute(rundir, os.projectdir())
        end
        if rundir then
            print(rundir)
        end
    end

    -- print end tag to ignore other deprecated/warnings infos
    print("__end__")
end
