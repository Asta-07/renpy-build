from renpybuild.model import task
import os
import subprocess
import re


@task(kind="host-python")
def copytree(c):
    c.copytree("{{ root }}/renios", "{{ renios }}")

    c.rmtree("{{ renios }}/prototype/prebuilt")
    c.rmtree("{{ renios }}/prototype/base")
    c.rmtree("{{ renios }}/prototype/prototype.xcodeproj/project.xcworkspace")
    c.rmtree("{{ renios }}/prototype/prototype.xcodeproj/xcshareddata")
    c.rmtree("{{ renios }}/prototype/prototype.xcodeproj/xcuserdata")

    c.run("""find {{ renios }}/prototype/ -name ._* -delete""")


def check_sdk(name, paths):

    for d in paths:
        fn = d / name

        p = subprocess.run([ "llvm-otool-13", "-l", f"{fn}"], capture_output=True)

        obj = None

        for l in p.stdout.decode("utf-8").split("\n"):
            if re.match(r'.*\.a\(.*\)', l):
                if obj is not None:
                    raise Exception(f"{obj} does not have a minos defined, in {fn}")

                obj = l

            if "minos" in l:
                obj = None


def lipo(c, namefilter):
    c.var("lipo", "llvm-lipo-13")

    paths = [
        c.path("{{ tmp }}/install.ios-arm64/lib"),
        ]

    c.var("paths", paths, expand=False)

    c.run("install -d {{ renios }}/prototype/prebuilt/release")

    for i in paths[0].glob("*.a"):
        if i.is_symlink():
            continue

        i = i.name

        if not namefilter(i):
            continue

        check_sdk(i, paths)

        print("(Release) Lipo and strip:", i)

        c.var("i", i)
        c.run("""
        {{ lipo }}
        -create
{% for p in paths %}
        {{ p }}/{{ i }}
{% endfor %}
        -output {{ renios }}/prototype/prebuilt/release/{{ i }}
        """)

        os.chmod(c.path("{{ renios }}/prototype/prebuilt/release/{{ i }}"), 0o755)

    # debug.

    paths = [
        c.path("{{ tmp }}/install.ios-sim-x86_64/lib"),
        c.path("{{ tmp }}/install.ios-sim-arm64/lib"),
        ]

    c.var("paths", paths, expand=False)

    c.run("install -d {{ renios }}/prototype/prebuilt/debug")

    for i in paths[0].glob("*.a"):
        if i.is_symlink():
            continue

        i = i.name

        if not namefilter(i):
            continue

        check_sdk(i, paths)

        print("(Debug) Lipo and strip:", i)

        c.var("i", i)
        c.run("""
        {{ lipo }}
        -create
{% for p in paths %}
        {{ p }}/{{ i }}
{% endfor %}
        -output {{ renios }}/prototype/prebuilt/debug/{{ i }}
        """)

        os.chmod(c.path("{{ renios }}/prototype/prebuilt/debug/{{ i }}"), 0o755)


@task(kind="host-python", platforms="ios")
def lipo_all(c):
    lipo(c, lambda n : True)


@task(kind="host-python", platforms="ios", always=True)
def lipo_renpy(c):
    lipo(c, lambda n : "librenpy" in n)


@task(kind="host-python", platforms="ios", always=True, pythons="2")
def copyback(c):
    c.copytree("{{ renios }}/prototype/prebuilt", "{{ root }}/renios/prototype/prebuilt")
