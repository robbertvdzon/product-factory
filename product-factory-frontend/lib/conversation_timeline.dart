import 'dart:async';
import 'package:flutter/material.dart';

typedef ChatMessage = Map<String, Object?>;

/// A bounded, newest-first viewport; loading history never moves the composer.
class ConversationTimeline extends StatefulWidget {
  const ConversationTimeline({
    super.key,
    required this.revision,
    required this.fetch,
    required this.title,
    required this.composer,
    required this.messageBuilder,
    this.introduction = const SizedBox.shrink(),
    this.status = const SizedBox.shrink(),
    this.latest = const SizedBox.shrink(),
  });
  final Object revision;
  final Future<Map<String, Object?>> Function({String? before, String? after})
  fetch;
  final String title;
  final Widget composer, introduction, status, latest;
  final Widget Function(ChatMessage message) messageBuilder;
  @override
  State<ConversationTimeline> createState() => _ConversationTimelineState();
}

class _ConversationTimelineState extends State<ConversationTimeline> {
  final scroll = ScrollController();
  List<ChatMessage> messages = [], pending = [];
  bool initial = true,
      loadingOlder = false,
      refreshing = false,
      hasOlder = false,
      unread = false;
  Object? failure;
  @override
  void initState() {
    super.initState();
    scroll.addListener(onScroll);
    unawaited(refresh());
  }

  @override
  void didUpdateWidget(covariant ConversationTimeline oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.revision != widget.revision) unawaited(refresh());
  }

  @override
  void dispose() {
    scroll.dispose();
    super.dispose();
  }

  List<ChatMessage> readMessages(Map<String, Object?> page) =>
      (page['messages'] as List? ?? [])
          .whereType<Map>()
          .map((m) => m.cast<String, Object?>())
          .toList();
  void onScroll() {
    if (!scroll.hasClients) return;
    if (scroll.position.pixels < 60 && unread) showLatest();
    if (scroll.position.maxScrollExtent > 100 &&
        scroll.position.pixels > scroll.position.maxScrollExtent - 120 &&
        hasOlder &&
        !loadingOlder &&
        failure == null) {
      unawaited(older());
    }
  }

  Future<void> refresh() async {
    if (refreshing) return;
    refreshing = true;
    final revision = widget.revision;
    var followedLatest = false;
    try {
      var cursor =
          (pending.lastOrNull ?? messages.lastOrNull)?['id'] as String?;
      var more = true;
      var added = false;
      while (more && mounted) {
        final page = await widget.fetch(after: cursor);
        if (!mounted) return;
        final nearBottom = !scroll.hasClients || scroll.offset < 80;
        followedLatest = nearBottom;
        final incoming = readMessages(page);
        final known = [...messages, ...pending].map((m) => m['id']).toSet();
        final fresh = incoming.where((m) => !known.contains(m['id'])).toList();
        setState(() {
          if (cursor == null) hasOlder = page['hasMore'] == true;
          if (nearBottom) {
            messages = [...messages, ...pending, ...fresh];
            pending = [];
            unread = false;
          } else {
            pending = [...pending, ...fresh];
          }
          initial = false;
          failure = null;
          added |= fresh.isNotEmpty;
          if (added && !nearBottom) unread = true;
        });
        more = cursor != null && page['hasMore'] == true && incoming.isNotEmpty;
        cursor = incoming.lastOrNull?['id'] as String?;
      }
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !scroll.hasClients || !added) return;
        if (followedLatest && scroll.offset < 80) scroll.jumpTo(0);
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          failure = e;
          initial = false;
        });
      }
    } finally {
      refreshing = false;
      if (mounted && widget.revision != revision) unawaited(refresh());
    }
  }

  void showLatest() {
    setState(() {
      messages = [...messages, ...pending];
      pending = [];
      unread = false;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && scroll.hasClients) scroll.jumpTo(0);
    });
  }

  Future<void> older() async {
    if (loadingOlder || !hasOlder || messages.isEmpty) return;
    setState(() => loadingOlder = true);
    try {
      final page = await widget.fetch(before: messages.first['id'] as String);
      if (!mounted) return;
      final known = messages.map((m) => m['id']).toSet();
      setState(() {
        messages = [
          ...readMessages(page).where((m) => !known.contains(m['id'])),
          ...messages,
        ];
        hasOlder = page['hasMore'] == true;
        failure = null;
      });
    } catch (e) {
      if (mounted) setState(() => failure = e);
    } finally {
      if (mounted) setState(() => loadingOlder = false);
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: LayoutBuilder(
        builder: (context, box) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.title, style: Theme.of(context).textTheme.titleMedium),
            widget.status,
            const SizedBox(height: 8),
            Expanded(
              child: Stack(
                children: [
                  if (initial)
                    const Center(child: CircularProgressIndicator())
                  else
                    ListView.builder(
                      key: const ValueKey('conversation-scroll'),
                      controller: scroll,
                      reverse: true,
                      primary: false,
                      itemCount: messages.length + 2,
                      itemBuilder: (context, index) {
                        if (index == 0) return widget.latest;
                        if (index == messages.length + 1) {
                          return Column(
                            children: [
                              if (hasOlder)
                                TextButton(
                                  onPressed: loadingOlder ? null : older,
                                  child: Text(
                                    loadingOlder
                                        ? 'Oudere berichten laden…'
                                        : 'Oudere berichten laden',
                                  ),
                                ),
                              if (!hasOlder) widget.introduction,
                              if (messages.isEmpty)
                                const Text(
                                  'Stel een vraag of beschrijf wat je wilt aanpassen.',
                                ),
                            ],
                          );
                        }
                        final m = messages[messages.length - index];
                        return KeyedSubtree(
                          key: ValueKey(m['id']),
                          child: widget.messageBuilder(m),
                        );
                      },
                    ),
                  if (unread)
                    Positioned(
                      bottom: 8,
                      right: 8,
                      child: FilledButton.tonal(
                        onPressed: () {
                          showLatest();
                        },
                        child: const Text('Nieuw bericht ↓'),
                      ),
                    ),
                ],
              ),
            ),
            if (failure != null)
              TextButton(
                onPressed: () {
                  if (messages.isEmpty) {
                    unawaited(refresh());
                  } else {
                    unawaited(older());
                    unawaited(refresh());
                  }
                },
                child: const Text('Berichten laden mislukt · opnieuw proberen'),
              ),
            const Divider(),
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: box.maxHeight * .48),
              child: SingleChildScrollView(
                primary: false,
                child: widget.composer,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
