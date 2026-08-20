# Linux File Permissions

## Understanding Permissions

### Permission Structure
```
-rwxr-xr-- 1 owner group size date filename
│├─┤├─┤├─┤
│ │  │  └── Others permissions (r--)
│ │  └───── Group permissions (r-x)
│ └──────── Owner permissions (rwx)
└────────── File type (- = file, d = directory, l = symlink)
```

### Permission Types
| Symbol | Permission | For Files | For Directories |
|--------|-----------|-----------|-----------------|
| r (4) | Read | View contents | List files |
| w (2) | Write | Modify contents | Create/delete files in dir |
| x (1) | Execute | Run as program | Enter directory (cd) |

---

## chmod - Change Mode

### Numeric (Octal) Method
```bash
chmod 755 file    # rwxr-xr-x (owner: full, group: r+x, others: r+x)
chmod 644 file    # rw-r--r-- (owner: r+w, group: r, others: r)
chmod 700 file    # rwx------ (owner: full, others: nothing)
chmod 777 file    # rwxrwxrwx (everyone: full - AVOID in production!)
chmod 600 file    # rw------- (owner: r+w only - good for secrets)
```

### Calculating Octal
```
r = 4, w = 2, x = 1

rwx = 4+2+1 = 7
rw- = 4+2+0 = 6
r-x = 4+0+1 = 5
r-- = 4+0+0 = 4
```

### Symbolic Method
```bash
chmod u+x file        # Add execute for owner
chmod g-w file        # Remove write for group
chmod o+r file        # Add read for others
chmod a+x file        # Add execute for all (a = all)
chmod u=rwx,g=rx file # Set specific permissions
chmod -R 755 dir      # Recursive
```

### Reference
| Who | Letter |
|-----|--------|
| Owner | u |
| Group | g |
| Others | o |
| All | a |

---

## chown - Change Ownership

```bash
chown user file              # Change owner
chown user:group file        # Change owner and group
chown :group file            # Change group only
chown -R user:group dir      # Recursive
```

---

## Special Permissions

### SUID (Set User ID) - 4
- When set on executable: runs with the file owner's permissions
- Used for programs that need elevated privileges
```bash
chmod 4755 file    # -rwsr-xr-x
chmod u+s file     # Set SUID
# Example: /usr/bin/passwd (runs as root to modify /etc/shadow)
```

### SGID (Set Group ID) - 2
- On executable: runs with file's group permissions
- On directory: new files inherit the directory's group
```bash
chmod 2755 dir     # drwxr-sr-x
chmod g+s dir      # Set SGID
```

### Sticky Bit - 1
- On directory: only file owner (or root) can delete their files
- Used for shared directories like /tmp
```bash
chmod 1777 dir     # drwxrwxrwt
chmod +t dir       # Set sticky bit
# Example: /tmp - anyone can write, but can't delete others' files
```

---

## Common Permission Patterns

| Permission | Octal | Use Case |
|-----------|-------|----------|
| rwxr-xr-x | 755 | Executable files, directories |
| rw-r--r-- | 644 | Regular files (config, source code) |
| rw------- | 600 | Private files (SSH keys, secrets) |
| rwx------ | 700 | Private directories |
| rwxrwxr-x | 775 | Shared directories (team) |
| rw-rw-r-- | 664 | Shared files (team) |

---

## Default Permissions: umask

### What is umask?
- Defines default permissions for new files
- Subtracts from maximum permissions
- Files max: 666 (no execute by default)
- Directories max: 777

### Calculation
```bash
umask 022     # Most common default

Files:      666 - 022 = 644 (rw-r--r--)
Directories: 777 - 022 = 755 (rwxr-xr-x)

umask 077
Files:      666 - 077 = 600 (rw-------)
Directories: 777 - 077 = 700 (rwx------)
```

---

## Key Interview Questions

**Q: What does `chmod 400 ~/.ssh/id_rsa` do and why?**
> Sets read-only for owner, no access for anyone else. SSH requires private keys to have restricted permissions. If permissions are too open, SSH refuses to use the key.

**Q: Can root bypass file permissions?**
> Yes, root can read/write any file regardless of permissions. However, root still needs execute permission to run a file as a program (unless using a workaround like `bash script.sh`).

**Q: What's the sticky bit and where is it used?**
> Sticky bit on a directory means only the file owner (or root) can delete files in it. `/tmp` is the classic example - all users can create files but can't delete each other's files.

**Q: File has permissions 000 but root can still read it. Why?**
> Root (UID 0) bypasses all permission checks. This is by design for system administration.
