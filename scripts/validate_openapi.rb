#!/usr/bin/env ruby
# Dependency-free structural checks for the checked-in OpenAPI document.
# This is deliberately narrower than a full OpenAPI schema/semantic validator.

require 'yaml'

file = ARGV.fetch(0, 'src/main/resources/openapi.yaml')
document = YAML.load_file(file)
errors = []

unless document.is_a?(Hash) && document['openapi'].to_s.start_with?('3.0.')
  errors << 'Expected an OpenAPI 3.0 document'
end

paths = document.is_a?(Hash) ? document['paths'] : nil
errors << 'paths must be a map' unless paths.is_a?(Hash)
schemas = document.is_a?(Hash) ? document.dig('components', 'schemas') : nil
errors << 'components.schemas must be a map' unless schemas.is_a?(Hash)

resolve_pointer = lambda do |pointer|
  next nil unless pointer.start_with?('#/')

  pointer.delete_prefix('#/').split('/').reduce(document) do |node, segment|
    node.is_a?(Hash) ? node[segment.gsub('~1', '/').gsub('~0', '~')] : nil
  end
end

visit = lambda do |node, location|
  case node
  when Hash
    if node.key?('$ref') && resolve_pointer.call(node['$ref']).nil?
      errors << "Unresolved local reference at #{location}: #{node['$ref']}"
    end
    node.each { |key, value| visit.call(value, "#{location}/#{key}") }
  when Array
    node.each_with_index { |value, index| visit.call(value, "#{location}/#{index}") }
  end
end
visit.call(document, '#')

operation_ids = {}
http_methods = %w[get post put patch delete head options trace]
(paths || {}).each do |path, item|
  unless path.start_with?('/') && item.is_a?(Hash)
    errors << "Invalid path item: #{path}"
    next
  end

  item.each do |method, operation|
    next unless http_methods.include?(method)

    unless operation.is_a?(Hash) && operation['responses'].is_a?(Hash) && !operation['responses'].empty?
      errors << "#{method.upcase} #{path} has no responses"
      next
    end

    id = operation['operationId']
    if id.nil? || id.empty?
      errors << "#{method.upcase} #{path} has no operationId"
    elsif operation_ids.key?(id)
      errors << "Duplicate operationId #{id}: #{operation_ids[id]} and #{method.upcase} #{path}"
    else
      operation_ids[id] = "#{method.upcase} #{path}"
    end

    path.scan(/\{([^}]+)\}/).flatten.each do |name|
      parameters = (item['parameters'] || []) + (operation['parameters'] || [])
      unless parameters.any? { |parameter| parameter['in'] == 'path' && parameter['name'] == name && parameter['required'] == true }
        errors << "#{method.upcase} #{path} lacks required path parameter #{name}"
      end
    end
  end
end

if errors.empty?
  puts "Structural OpenAPI checks passed: #{paths.size} paths, #{operation_ids.size} operations, #{schemas.size} schemas"
else
  warn errors.join("\n")
  exit 1
end
