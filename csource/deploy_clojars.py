import subprocess
import os

env = os.environ

resource_prefix = {
    'macos': 'darwin',
}.get(env['platform'], env['platform'])

resource_suffix = {
    'arm64': 'aarch64',
    'x86_64' : 'x86-64',
}[env['arch']]


subprocess.run(['cp',
                'libmembraneskia-{arch}.{shared_suffix}'.format(arch=env['arch'],shared_suffix=env['shared_suffix']),
                '{platform}-{resource_suffix}/resources/{resource_prefix}-{resource_suffix}/libmembraneskia.{shared_suffix}'.format(
                    platform=env['platform'],
                    resource_suffix=resource_suffix,
                    resource_prefix=resource_prefix,
                    shared_suffix=env['shared_suffix
                    '],
                )], check=True)

# cp libmembraneskia-${arch}.${shared_suffix} ${platform}-${resource_suffix}/resources/${resource_prefix}-${resource_suffix}/libmembraneskia.${shared_suffix}

with open('../project.clj') as f:
    s = f.read()
    version = re.search(r'"([^"]*)"', s)[1]

print('using version: "{version}"'.format(version=version))

os.chdir('{platform}-{resource_suffix}'.format(platform=env['platform'], resource_suffix=resource_suffix) )

subprocess.run(['clojure', '-X:jar', ':sync-pom', 'true', ':version', '"{version}"'.format(version=version)],
               check=True)

subprocess.run(['clojure', '-M:deploy'])

