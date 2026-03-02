import os
import glob
import subprocess

def get_added_lines():
    result = subprocess.run(['git', 'diff', '--staged', './include/osh-core/sensorhub-webui-core/src/main/resources/org/sensorhub/ui/i18n/messages.properties'], stdout=subprocess.PIPE, text=True)
    lines = result.stdout.split('\n')
    added = []
    for line in lines:
        if line.startswith('+') and not line.startswith('+++'):
            content = line[1:]
            if content.strip():
                added.append(content)
    return added

def to_ascii_escape(s):
    res = ""
    for c in s:
        code = ord(c)
        if code > 127:
            res += f'\\u{code:04x}'
        else:
            res += c
    return res

if __name__ == '__main__':
    added_lines = get_added_lines()
    print(f"Found {len(added_lines)} lines to append.")

    if len(added_lines) > 0:
        target_dir = './include/osh-core/sensorhub-webui-core/src/main/resources/org/sensorhub/ui/i18n/'
        files = glob.glob(os.path.join(target_dir, 'messages_*.properties'))

        for fpath in files:
            with open(fpath, 'a', encoding='ascii') as f:
                f.write('\n')
                for line in added_lines:
                    f.write(to_ascii_escape(line) + '\n')
            print(f"Appended to {os.path.basename(fpath)}")
