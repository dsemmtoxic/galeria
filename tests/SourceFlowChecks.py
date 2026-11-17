#!/usr/bin/env python3
"""Static source invariants only; not Android runtime tests."""
from pathlib import Path
b=Path(__file__).resolve().parents[1]/'app/src/main/java/app/galeria'
main=(b/'MainActivity.java').read_text()
viewer=(b/'ViewerActivity.java').read_text()
zoom=(b/'ZoomImageView.java').read_text()
player=(b/'PlayerSurface.java').read_text()
prefs=(b/'Prefs.java').read_text()
actions=(b/'MediaActions.java').read_text()
transfers=(b/'FolderTransfers.java').read_text()
search=(b/'SearchActivity.java').read_text()
assert 'tab=0;filter=0;albumKey=null;query="";trash=false;' in main
assert 'prefs.integer("tab"' not in main
assert 'prefs.set("tab"' not in main
assert 'onNewIntent' in main and 'Intent.CATEGORY_LAUNCHER' in main
assert 'boolean canNavigate(int direction)' in zoom and 'canNavigate(int direction)' in viewer
assert 'if(listener.canNavigate(direction))' in zoom and 'else elasticReturn(start)' in zoom
assert 'gestures.canNavigate(direction)' in player
assert 'Primeiro arquivo.' not in viewer and 'Último arquivo.' not in viewer
assert 's.kind==SearchIndex.ALBUM){setResult(' not in search
assert 'recent_destination_albums' in prefs and 'Math.min(5,list.size())' in prefs
assert 'new AlbumPicker(' in actions and 'prefs.rememberAlbum(album.key)' in actions
assert 'send(Context c,List<MediaItem> items,MediaItem.Album album' in transfers
assert 'FileAccess.albumFolder(album)' in transfers
print('Verificações estáticas passaram (não substituem compilação/teste no Android).')
