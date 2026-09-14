import 'dart:async';
import 'dart:js_interop';
import 'package:web/web.dart' as web;

Future<List<Map<String, Object?>>> pickConversationImages() async {
  final input = web.HTMLInputElement()
    ..type = 'file'
    ..accept = 'image/png,image/jpeg,image/webp'
    ..multiple = true;
  final selected = Completer<List<Map<String, Object?>>>();
  input.addEventListener(
    'cancel',
    ((web.Event _) {
      if (!selected.isCompleted) selected.complete([]);
    }).toJS,
  );
  input.addEventListener(
    'change',
    ((web.Event _) {
      unawaited(() async {
        try {
          final files = input.files;
          final result = <Map<String, Object?>>[];
          if ((files?.length ?? 0) > 6) {
            throw StateError('Kies maximaal zes afbeeldingen.');
          }
          for (var i = 0; i < (files?.length ?? 0); i++) {
            final file = files!.item(i)!;
            if (file.size > 4 * 1024 * 1024) {
              throw StateError('Een afbeelding mag maximaal 4 MB zijn.');
            }
            final read = Completer<String>();
            final reader = web.FileReader();
            reader.addEventListener(
              'load',
              ((web.Event _) {
                read.complete((reader.result as JSString).toDart);
              }).toJS,
            );
            reader.addEventListener(
              'error',
              ((web.Event _) {
                read.completeError(
                  StateError('De afbeelding kon niet worden gelezen.'),
                );
              }).toJS,
            );
            reader.readAsDataURL(file);
            result.add({
              'filename': file.name,
              'mediaType': file.type,
              'base64': (await read.future).split(',').last,
            });
          }
          selected.complete(result);
        } catch (error, stack) {
          selected.completeError(error, stack);
        }
      }());
    }).toJS,
  );
  input.click();
  return selected.future;
}
