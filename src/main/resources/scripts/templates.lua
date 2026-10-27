-- imports
import("core.base.json")
import("core.project.template")

function main ()

    -- collect the template kinds of every language the installation offers
    local result = {}
    for _, language in ipairs(template.languages()) do
        local kinds = {}
        for _, instance in ipairs(template.templates(language)) do
            table.insert(kinds, instance:name())
        end
        result[language] = kinds
    end
    print(json.encode(result))
end
