# 使用自己的 SSH 密钥连接 Google Cloud

## 一、在服务器创建用户

先通过 Google Cloud 网页 SSH 登录服务器，只需要初始化这一次。

创建用户：

```bash
sudo useradd --badname -m -s /bin/bash 1661219752
```

`1661219752` 是纯数字用户名，所以需要加 `--badname`。如果提示用户已经存在，直接继续下一步。

授予最高权限：

```bash
sudo usermod -aG google-sudoers 1661219752
```

## 二、把 Mac 公钥放到服务器

在 Mac 终端查看并复制公钥：

```bash
cat ~/.ssh/id_ed25519.pub
```

回到 Google Cloud 网页 SSH，执行：

```bash
sudo mkdir -p /home/1661219752/.ssh
echo '<粘贴刚才复制的完整公钥>' | sudo tee /home/1661219752/.ssh/authorized_keys >/dev/null
sudo chown -R --reference=/home/1661219752 /home/1661219752/.ssh
sudo chmod 700 /home/1661219752/.ssh
sudo chmod 600 /home/1661219752/.ssh/authorized_keys
```

只复制公钥 `id_ed25519.pub`，不要上传私钥 `id_ed25519`。

## 三、使用 Termius 连接

在 Termius 的 `Keychain` 中导入 Mac 私钥：

```text
/Users/xiongtao/.ssh/id_ed25519
```

主机填写：

```text
Address: 35.187.157.90
Port: 22
Username: 1661219752
Key: id_ed25519
```

然后点击 `Connect`。

## 四、使用 Mac 自带 SSH 连接

不使用 Termius 也可以直接执行：

```bash
ssh -i ~/.ssh/id_ed25519 1661219752@35.187.157.90
```

登录后需要最高权限时执行：

```bash
sudo -i
```

> 如果服务器停止后重新启动，临时外部 IP 可能变化，需要把文档和 Termius 中的 IP 改成新的地址。
