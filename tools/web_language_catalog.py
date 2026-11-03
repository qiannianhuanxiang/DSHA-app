"""Read static UI dictionary literals from locked DSH browser bundles.

Only the JSON-like literal grammar is accepted; bundles are never executed.
Namespaced message IDs remain stable when the upstream source changes order.
"""
import hashlib
import json
import re
from pathlib import Path

LANGUAGES = ('zh', 'zh-Hant', 'en', 'ru', 'ja', 'ko', 'ar', 'pt', 'la', 'fr')
EXTRA_LANGUAGES = tuple(tag for tag in LANGUAGES if tag not in ('zh', 'en'))
SPACE = re.compile(r'(?:\s+|/\*[\s\S]*?\*/|//[^\n]*\n)')
IDENTIFIER = re.compile(r'[a-zA-Z_$][\w$]*')
DECODER = json.JSONDecoder()


class Literal:
    def __init__(self, text, names=None, offset=0):
        self.text, self.names, self.at = text, names or {}, offset

    def space(self):
        while self.at < len(self.text):
            match = SPACE.match(self.text, self.at)
            if not match:
                return
            self.at += len(match.group())

    def value(self):
        self.space()
        char = self.text[self.at]
        if char == '"':
            value, self.at = DECODER.raw_decode(self.text, self.at)
            return value
        if char in '{[':
            self.at += 1
            result = {} if char == '{' else []
            close = '}' if char == '{' else ']'
            while True:
                self.space()
                if self.text[self.at] == close:
                    self.at += 1
                    if char == '[' and self.text.startswith('.join(', self.at):
                        self.at += len('.join(')
                        separator = self.value()
                        self.space()
                        if not isinstance(separator, str) or self.text[self.at] != ')':
                            raise ValueError('WEB_LITERAL_JOIN')
                        self.at += 1
                        return separator.join(result)
                    return result
                if char == '{':
                    if self.text.startswith('...', self.at):
                        self.at += 3
                        result.update(self.value())
                        self.space()
                        if self.text[self.at] == ',':
                            self.at += 1
                        elif self.text[self.at] != close:
                            raise ValueError('WEB_LITERAL_SEPARATOR')
                        continue
                    key = self.key()
                    self.space()
                    if self.text[self.at] == ':':
                        self.at += 1
                        result[key] = self.value()
                    else:
                        result[key] = self.names[key]
                else:
                    result.append(self.value())
                self.space()
                if self.text[self.at] == ',':
                    self.at += 1
                elif self.text[self.at] != close:
                    raise ValueError('WEB_LITERAL_SEPARATOR')
        name = self.key()
        return self.names[name]

    def key(self):
        self.space()
        if self.text[self.at] == '"':
            return self.value()
        match = IDENTIFIER.match(self.text, self.at)
        if not match:
            raise ValueError('WEB_LITERAL_IDENTIFIER')
        self.at += len(match.group())
        return match.group()


def static_names(source):
    names = {}
    for match in re.finditer(r'\b(?:const|let|var)\s+([\w$]+)\s*=\s*', source):
        try:
            reader = Literal(source, names, match.end())
            value = reader.value()
            if isinstance(value, str) or (isinstance(value, (dict, list)) and value):
                names[match[1]] = value
        except (ValueError, KeyError, IndexError, json.JSONDecodeError):
            pass
    return names


def dictionaries(source):
    names = static_names(source)
    rows = []
    for match in re.finditer(r'\blocale\.register\(\s*', source):
        reader = Literal(source, names, match.end())
        namespace = reader.value()
        reader.space()
        if reader.text[reader.at] != ',':
            raise ValueError('WEB_REGISTER_SEPARATOR')
        reader.at += 1
        try:
            values = reader.value()
        except KeyError:
            # The directory browser's locale/dictionary loop is handled below.
            if namespace != 'directory-browser':
                raise
            values = dict(names['dictionaries'])
        if not isinstance(namespace, str) or not isinstance(values, dict) or set(values) != {'zh', 'en'}:
            raise ValueError('WEB_REGISTER_LITERAL: ' + str(namespace))
        if set(values['zh']) != set(values['en']):
            raise ValueError('WEB_REGISTER_KEYS: ' + namespace)
        if not all(isinstance(text, str) for entries in values.values() for text in entries.values()):
            raise ValueError('WEB_REGISTER_TEXT: ' + namespace)
        rows.append((namespace, values))
    return rows


def extract(modules, transform=lambda relative, data: data):
    messages, inputs, namespaces = [], {}, set()
    for source in sorted((Path(modules) / '@deepseek-ai').glob('*/lib/client*.js')):
        raw = source.read_bytes()
        if b'locale.register(' not in raw:
            continue
        relative = source.relative_to(modules).as_posix()
        prepared = transform(relative, raw)
        found = dictionaries(prepared.decode('utf-8'))
        if not found:
            continue
        inputs[relative] = hashlib.sha256(raw).hexdigest()
        for namespace, values in found:
            if namespace in namespaces:
                raise ValueError('WEB_NAMESPACE_DUPLICATE: ' + namespace)
            namespaces.add(namespace)
            for key in sorted(values['zh']):
                messages.append({'id': namespace + ':' + key, 'namespace': namespace,
                                 'key': key, 'zh': values['zh'][key], 'en': values['en'][key]})
    messages.sort(key=lambda row: row['id'])
    return {'schema': 1, 'dshVersion': '0.2.1-rc.3', 'inputs': inputs, 'messages': messages}
