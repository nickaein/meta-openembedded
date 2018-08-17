SUMMARY = "Kernel selftest for Linux"
DESCRIPTION = "Kernel selftest for Linux"
LICENSE = "GPLv2"

LIC_FILES_CHKSUM = "file://COPYING;md5=d7810fab7487fb0aad327b76f1be7cd7"

DEPENDS = "rsync-native"

# for musl libc
SRC_URI_libc-musl += "file://userfaultfd.patch \
                      file://0001-bpf-test_progs.c-add-support-for-musllibc.patch \
"

# now we just test bpf and vm
# we will append other kernel selftest in the future
# bpf was added in 4.10 with: https://github.com/torvalds/linux/commit/5aa5bd14c5f8660c64ceedf14a549781be47e53d
# if you have older kernel than that you need to remove it from PACKAGECONFIG
PACKAGECONFIG ??= "bpf vm"

PACKAGECONFIG[bpf] = ",,elfutils libcap libcap-ng rsync-native,"
PACKAGECONFIG[vm] = ",,,libgcc bash"

do_patch[depends] += "virtual/kernel:do_shared_workdir"

inherit linux-kernel-base kernel-arch

do_populate_lic[depends] += "virtual/kernel:do_patch"

S = "${WORKDIR}/${BP}"

TEST_LIST = " \
    ${@bb.utils.filter('PACKAGECONFIG', 'bpf vm', d)} \
"

EXTRA_OEMAKE = '\
    CROSS_COMPILE=${TARGET_PREFIX} \
    ARCH=${ARCH} \
    CC="${CC}" \
    AR="${AR}" \
    LD="${LD}" \
    DESTDIR="${D}" \
'

KERNEL_SELFTEST_SRC ?= "Makefile \
                        include \
                        tools \
                        scripts \
                        arch \
			COPYING \
"

python __anonymous () {
    import re

    var = d.getVar('TARGET_CC_ARCH')
    pattern = '_FORTIFY_SOURCE=[^0]'

    if re.search(pattern, var):
        d.appendVar('TARGET_CC_ARCH', " -O")
}

do_compile() {
    for i in ${TEST_LIST}
    do
        oe_runmake -C ${S}/tools/testing/selftests/${i}
    done
}

do_install() {
    for i in ${TEST_LIST}
    do
        oe_runmake -C ${S}/tools/testing/selftests/${i} INSTALL_PATH=${D}/usr/kernel-selftest/${i} install
    done

    chown root:root  -R ${D}/usr/kernel-selftest
}

do_configure() {
    :
}

do_patch[prefuncs] += "copy_kselftest_source_from_kernel remove_unrelated"
python copy_kselftest_source_from_kernel() {
    sources = (d.getVar("KERNEL_SELFTEST_SRC") or "").split()
    src_dir = d.getVar("STAGING_KERNEL_DIR")
    dest_dir = d.getVar("S")
    bb.utils.mkdirhier(dest_dir)
    for s in sources:
        src = oe.path.join(src_dir, s)
        dest = oe.path.join(dest_dir, s)
        if os.path.isdir(src):
            oe.path.copytree(src, dest)
        else:
            bb.utils.copyfile(src, dest)
}

remove_unrelated() {
    if ${@bb.utils.contains('PACKAGECONFIG','bpf','true','false',d)} ; then
        test -f ${S}/tools/testing/selftests/bpf/Makefile && \
            sed -i -e '/test_pkt_access/d' -e '/test_pkt_md_access/d' -e '/sockmap_verdict_prog/d' ${S}/tools/testing/selftests/bpf/Makefile || \
            bberror "Your kernel is probably older than 4.10 and doesn't have tools/testing/selftests/bpf/Makefile file from https://github.com/torvalds/linux/commit/5aa5bd14c5f8660c64ceedf14a549781be47e53d, disable bpf PACKAGECONFIG"
    fi
}

PACKAGE_ARCH = "${MACHINE_ARCH}"

INHIBIT_PACKAGE_DEBUG_SPLIT="1"
FILES_${PN} += "/usr/kernel-selftest"

# tools/testing/selftests/vm/Makefile doesn't respect LDFLAGS and tools/testing/selftests/Makefile explicitly overrides to empty
INSANE_SKIP_${PN} += "ldflags"
