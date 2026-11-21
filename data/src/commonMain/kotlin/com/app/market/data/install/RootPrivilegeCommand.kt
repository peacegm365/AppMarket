package com.app.market.data.install

/** Root 提权命令：默认 su，可在设置里改成 ksud/apatch 等自有实现。 */
object RootPrivilegeCommand {
    @Volatile
    var command: String = "su"
}
