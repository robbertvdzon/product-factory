import importlib.util
from pathlib import Path
import tarfile
import tempfile
import unittest
from urllib.parse import parse_qs, urlparse


MODULE_PATH = Path(__file__).with_name("docker_engine_build.py")
SPEC = importlib.util.spec_from_file_location("docker_engine_build", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
docker_engine_build = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(docker_engine_build)


class DockerEngineBuildTest(unittest.TestCase):
    def test_context_archive_honours_dockerignore(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            context = Path(temp_dir) / "context"
            context.mkdir()
            (context / "Dockerfile").write_text("FROM scratch\n", encoding="utf-8")
            (context / ".dockerignore").write_text("build\n", encoding="utf-8")
            (context / "kept.txt").write_text("safe\n", encoding="utf-8")
            (context / "build").mkdir()
            (context / "build" / "ignored.txt").write_text(
                "ignore\n", encoding="utf-8"
            )
            archive = Path(temp_dir) / "context.tar"

            docker_engine_build._create_context_archive(context, archive)

            with tarfile.open(archive) as tar:
                names = tar.getnames()
            self.assertIn("./Dockerfile", names)
            self.assertIn("./kept.txt", names)
            self.assertNotIn("./build/ignored.txt", names)

    def test_build_path_passes_all_metadata_as_build_args(self) -> None:
        build_args = {
            "BUILD_ENVIRONMENT": "preview",
            "SOURCE_REVISION": "0123456789abcdef0123456789abcdef01234567",
            "DEPLOYED_AT": "2026-08-15T18:00:00Z",
        }

        path = docker_engine_build._build_path("Dockerfile", build_args)

        query = parse_qs(urlparse(path).query)
        self.assertEqual(query["dockerfile"], ["Dockerfile"])
        self.assertEqual(
            docker_engine_build.json.loads(query["buildargs"][0]), build_args
        )

    def test_invalid_build_arg_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "NAAM=WAARDE"):
            docker_engine_build._parse_build_args(["BUILD_ENVIRONMENT"])


if __name__ == "__main__":
    unittest.main()
