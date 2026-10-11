package cn.hcy.gopan;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class LocalFiles extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {try{File root=new File(getContext().getFilesDir(),"downloads").getCanonicalFile();File f=new File(root,uri.getLastPathSegment()).getCanonicalFile();if(!f.getParentFile().equals(root)||!f.isFile())throw new FileNotFoundException();return f;}catch(IOException e){throw new FileNotFoundException();}}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public String getType(Uri uri){String ext=android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return mime==null?"application/octet-stream":mime;}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=file(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName().replaceFirst("^\\d+-",""),f.length()});return c;}catch(FileNotFoundException e){return null;}}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
