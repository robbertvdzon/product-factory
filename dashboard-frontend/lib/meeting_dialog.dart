import 'dart:async';
import 'package:flutter/material.dart';
import 'api.dart';
import 'formatting.dart';

/// Interactief overlegvenster: chat tussen de producteigenaar en de AI die het product runt.
/// Elk bericht is een synchrone, blokkerende aanroep (de agentworker ondersteunt geen sessies,
/// dus elke beurt is een verse AI-aanroep met de volledige transcript als context) die tot enkele
/// minuten kan duren — vandaar de expliciete "AI denkt na…"-indicator terwijl de invoer op slot zit.
class MeetingDialog extends StatefulWidget {
  const MeetingDialog({
    required this.api,
    required this.productSlug,
    required this.meetingId,
    super.key,
  });

  final DashboardApi api;
  final String productSlug;
  final String meetingId;

  @override
  State<MeetingDialog> createState() => _MeetingDialogState();
}

class _MeetingDialogState extends State<MeetingDialog> {
  late Future<List<dynamic>> data;
  Timer? refreshTimer;
  final _controller = TextEditingController();
  final _scrollController = ScrollController();
  bool _sending = false;
  bool _closing = false;
  String? _error;
  int _lastRenderedMessageCount = 0;

  @override
  void initState() {
    super.initState();
    _reload();
    refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted && !_sending) setState(_reload);
    });
  }

  void _reload() {
    data = Future.wait<dynamic>([
      widget.api.meeting(widget.productSlug, widget.meetingId),
      widget.api.meetingMessages(widget.productSlug, widget.meetingId),
    ]);
  }

  @override
  void dispose() {
    refreshTimer?.cancel();
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
      );
    });
  }

  Future<void> _send() async {
    final content = _controller.text.trim();
    if (content.isEmpty || _sending) return;
    setState(() {
      _sending = true;
      _error = null;
    });
    _controller.clear();
    try {
      await widget.api.sendMeetingMessage(
        widget.productSlug,
        widget.meetingId,
        content,
      );
      if (mounted) {
        setState(() {
          _sending = false;
          _reload();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _sending = false;
          _error = '$error';
        });
      }
    }
  }

  Future<void> _close() async {
    setState(() => _closing = true);
    try {
      await widget.api.closeMeeting(widget.productSlug, widget.meetingId);
      if (mounted) {
        setState(() {
          _closing = false;
          _reload();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() => _closing = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<List<dynamic>>(
    future: data,
    builder: (context, snapshot) {
      final result = snapshot.data;
      final meeting = result?[0] as Map<String, dynamic>?;
      final messages =
          (result?[1] as List<dynamic>?)?.cast<Map<String, dynamic>>() ??
          const [];
      final status = meeting?['status'] as String?;
      final open = status == 'OPEN';
      final initiator = meeting?['initiator'] as String?;
      final topics =
          ((meeting?['requestedTopics'] as List<dynamic>?) ?? const [])
              .cast<String>();

      if (messages.length > _lastRenderedMessageCount) {
        _lastRenderedMessageCount = messages.length;
        _scrollToBottom();
      }

      return AlertDialog(
        title: Row(
          children: [
            Expanded(child: Text('Overleg ${meeting?['sequenceNumber'] ?? ''}')),
            if (initiator == 'product')
              Tooltip(
                message: topics.isEmpty
                    ? 'Door het product aangevraagd'
                    : topics.map((topic) => '• $topic').join('\n'),
                child: const Chip(label: Text('Door product aangevraagd')),
              ),
          ],
        ),
        content: SizedBox(
          width: 700,
          height: 640,
          child: Builder(
            builder: (context) {
              if (!snapshot.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError) {
                return Center(
                  child: Text('Overleg kon niet laden: ${snapshot.error}'),
                );
              }
              return Column(
                children: [
                  Expanded(
                    child: messages.isEmpty
                        ? const Center(
                            child: Text(
                              'Nog geen berichten. Typ hieronder je eerste bericht.',
                            ),
                          )
                        : ListView.builder(
                            controller: _scrollController,
                            itemCount: messages.length,
                            itemBuilder: (context, index) =>
                                _MessageBubble(message: messages[index]),
                          ),
                  ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Text(
                        _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  if (_sending) ...[
                    const LinearProgressIndicator(),
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 4),
                      child: Text('AI denkt na…'),
                    ),
                  ],
                  if (open) ...[
                    const SizedBox(height: 8),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _controller,
                            enabled: !_sending,
                            minLines: 1,
                            maxLines: 4,
                            decoration: const InputDecoration(
                              hintText: 'Typ een bericht…',
                              border: OutlineInputBorder(),
                            ),
                            onSubmitted: (_) => _send(),
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton.filled(
                          onPressed: _sending ? null : _send,
                          icon: const Icon(Icons.send),
                        ),
                      ],
                    ),
                  ] else
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 8),
                      child: Text('Dit overleg is afgesloten.'),
                    ),
                ],
              );
            },
          ),
        ),
        actions: [
          if (open)
            FilledButton.icon(
              onPressed: _closing || _sending ? null : _close,
              icon: const Icon(Icons.check_circle_outline),
              label: const Text('Overleg afsluiten'),
            ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Sluiten'),
          ),
        ],
      );
    },
  );
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message});
  final Map<String, dynamic> message;

  @override
  Widget build(BuildContext context) {
    final fromOwner = message['sender'] == 'owner';
    return Align(
      alignment: fromOwner ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.all(12),
        constraints: const BoxConstraints(maxWidth: 480),
        decoration: BoxDecoration(
          color: fromOwner
              ? Theme.of(context).colorScheme.primaryContainer
              : Theme.of(context).colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              fromOwner ? 'Jij' : 'AI',
              style: Theme.of(context).textTheme.labelSmall,
            ),
            const SizedBox(height: 4),
            Text('${message['content']}'),
            const SizedBox(height: 4),
            Text(
              formatDateTime(message['createdAt']),
              style: Theme.of(context).textTheme.labelSmall,
            ),
          ],
        ),
      ),
    );
  }
}
