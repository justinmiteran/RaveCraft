# Opens the modern Windows folder dialog (IFileOpenDialog with FOS_PICKFOLDERS), which follows the system theme.
# Input: RAVECRAFT_DIALOG_TITLE and RAVECRAFT_DIALOG_START environment variables (passed this way to avoid quoting).
# Output: the chosen folder on stdout (UTF-8) and exit code 0; exit code 1 when cancelled; anything else is a failure.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class RaveCraftFolderDialog {
	[ComImport, Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
	private class FileOpenDialog {}

	[ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	private interface IFileDialog {
		[PreserveSig] int Show(IntPtr parent);
		void SetFileTypes(uint count, IntPtr filters);
		void SetFileTypeIndex(uint index);
		void GetFileTypeIndex(out uint index);
		void Advise(IntPtr events, out uint cookie);
		void Unadvise(uint cookie);
		void SetOptions(uint options);
		void GetOptions(out uint options);
		void SetDefaultFolder(IShellItem item);
		void SetFolder(IShellItem item);
		void GetFolder(out IShellItem item);
		void GetCurrentSelection(out IShellItem item);
		void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name);
		void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string name);
		void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
		void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
		void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
		void GetResult(out IShellItem item);
	}

	[ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	private interface IShellItem {
		void BindToHandler(IntPtr bindContext, ref Guid handler, ref Guid riid, out IntPtr result);
		void GetParent(out IShellItem parent);
		void GetDisplayName(uint form, [MarshalAs(UnmanagedType.LPWStr)] out string name);
	}

	[DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
	private static extern void SHCreateItemFromParsingName(string path, IntPtr bindContext, ref Guid riid,
			out IShellItem item);

	[DllImport("user32.dll")]
	private static extern IntPtr GetForegroundWindow();

	private const uint FOS_PICKFOLDERS = 0x20;
	private const uint FOS_FORCEFILESYSTEM = 0x40;
	private const uint SIGDN_FILESYSPATH = 0x80058000;
	private const int ERROR_CANCELLED = unchecked((int) 0x800704C7);

	/** The chosen folder, or null when cancelled. */
	public static string Pick(string title, string start) {
		IFileDialog dialog = (IFileDialog) new FileOpenDialog();
		try {
			uint options;
			dialog.GetOptions(out options);
			dialog.SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
			if (!String.IsNullOrEmpty(title)) {
				dialog.SetTitle(title);
			}
			if (!String.IsNullOrEmpty(start)) {
				try {
					Guid shellItem = typeof(IShellItem).GUID;
					IShellItem folder;
					SHCreateItemFromParsingName(start, IntPtr.Zero, ref shellItem, out folder);
					dialog.SetFolder(folder);
				} catch (Exception) {
					// a start folder that no longer exists: the dialog opens where it last was
				}
			}
			int result = dialog.Show(GetForegroundWindow());
			if (result == ERROR_CANCELLED) {
				return null;
			}
			Marshal.ThrowExceptionForHR(result);
			IShellItem chosen;
			dialog.GetResult(out chosen);
			string path;
			chosen.GetDisplayName(SIGDN_FILESYSPATH, out path);
			return path;
		} finally {
			Marshal.ReleaseComObject(dialog);
		}
	}
}
'@
if ($env:RAVECRAFT_DIALOG_COMPILE_ONLY) {
	exit 0
}
$chosen = [RaveCraftFolderDialog]::Pick($env:RAVECRAFT_DIALOG_TITLE, $env:RAVECRAFT_DIALOG_START)
if ($null -eq $chosen) {
	exit 1
}
[Console]::Out.Write($chosen)
exit 0
