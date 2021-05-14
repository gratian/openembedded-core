SUMMARY = "Linux kernel Development Scripts"
DESCRIPTION = "Development scripts for the linux kernel. When built, this recipe packages the \
scripts of the selected kernel and makes it available for full kernel \
development or external module builds"

SECTION = "kernel"

LICENSE = "GPLv2"

KERNEL_DIRECTORY = "${STAGING_KERNEL_DIR}"
KERNEL_BUILD_DIRECTORY = "${STAGING_KERNEL_BUILDDIR}"

inherit linux-kernel-base

# Whilst not a module, this ensures we don't get multilib extended (which would make no sense)
inherit module-base

do_configure[depends] = "kernel-devsrc:do_install"

# There's nothing to do here, except install the scripts where we can package it
do_fetch[noexec] = "1"
do_unpack[noexec] = "1"
do_patch[noexec] = "1"
do_configure[noexec] = "1"
do_compile[noexec] = "1"
deltask do_populate_sysroot

S = "${KERNEL_DIRECTORY}"
B = "${KERNEL_BUILD_DIRECTORY}"

KERNEL_VERSION = "${@get_kernelversion_headers('${B}')}"

PACKAGE_ARCH = "${MACHINE_ARCH}"

KERNEL_BUILD_ROOT="${nonarch_base_libdir}/modules/"

# Skip creating empty '-dbg' package
INHIBIT_PACKAGE_DEBUG_SPLIT="1"

do_install() {
    kerneldir=${D}${KERNEL_BUILD_ROOT}${KERNEL_VERSION}
    install -d $kerneldir/build

    # copy in parts from the build that we'll need later
    (
	cd ${B}

	# This scripts copy blow up QA, so for now, we require a more
	# complex 'make scripts' to restore these, versus copying them
	# here. Left as a reference to indicate that we know the scripts must
	# be dealt with.
	# cp -a scripts $kerneldir/build

        if [ -d arch/${ARCH}/scripts ]; then
	    cp -a arch/${ARCH}/scripts $kerneldir/build/arch/${ARCH}
	fi

	rm -f $kerneldir/build/scripts/*.o
	rm -f $kerneldir/build/scripts/*/*.o
    )

    # grab the chunks from the source tree that we need
    (
	cd ${S}
	cp -a scripts $kerneldir/build
    )

    # make the scripts python3 safe. We won't be running these, and if they are
    # left as /usr/bin/python rootfs assembly will fail, since we only have python3
    # in the RDEPENDS (and the python3 package does not include /usr/bin/python)
    for ss in $(find $kerneldir/build/scripts -type f -name '*'); do
	sed -i 's,/usr/bin/python2,/usr/bin/env python3,' "$ss"
	sed -i 's,/usr/bin/env python2,/usr/bin/env python3,' "$ss"
	sed -i 's,/usr/bin/python,/usr/bin/env python3,' "$ss"
    done

    chown -R root:root ${D}
}

# Ensure we don't race against "make scripts" during cpio
do_install[lockfiles] = "${TMPDIR}/kernel-scripts.lock"

FILES_${PN} = "${KERNEL_BUILD_ROOT} ${KERNEL_SRC_PATH}"
FILES_${PN}-dbg += "${KERNEL_BUILD_ROOT}*/build/scripts/*/.debug/*"

RDEPENDS_${PN} = "bc python3 flex bison ${TCLIBC}-utils"
# 4.15+ needs these next two RDEPENDS
RDEPENDS_${PN} += "openssl-dev util-linux"
# and x86 needs a bit more for 4.15+
RDEPENDS_${PN} += "${@bb.utils.contains('ARCH', 'x86', 'elfutils', '', d)}"
