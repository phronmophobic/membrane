import subprocess
import os
import sys
import re

os.chdir(os.path.dirname(sys.argv[0]))

env = os.environ

resource_prefix = {
    'macos': 'darwin',
}.get(env['platform'], env['platform'])

resource_suffix = {
    'arm64': 'aarch64',
    'x86_64' : 'x86-64',
}[env['arch']]

def run(args):
    print(args)
    subprocess.run(args, check=True)

run(['cp',
     'libmembraneskia-{arch}.{shared_suffix}'.format(arch=env['arch'],shared_suffix=env['shared_suffix']),
     '{platform}-{resource_suffix}/resources/{resource_prefix}-{resource_suffix}/libmembraneskia.{shared_suffix}'.format(
         platform=env['platform'],
         resource_suffix=resource_suffix,
         resource_prefix=resource_prefix,
         shared_suffix=env['shared_suffix'],
     )])

# cp libmembraneskia-${arch}.${shared_suffix} ${platform}-${resource_suffix}/resources/${resource_prefix}-${resource_suffix}/libmembraneskia.${shared_suffix}

version="0.12-beta"

print('using version: "{version}"'.format(version=version))

os.chdir('{platform}-{resource_suffix}'.format(platform=env['platform'], resource_suffix=resource_suffix) )

run(['clojure', '-X:jar', ':sync-pom', 'true', ':version', '"{version}"'.format(version=version)])

run(['clojure', '-M:deploy'])

