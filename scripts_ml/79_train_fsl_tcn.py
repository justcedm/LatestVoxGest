"""Train the clean non-residual FSL TCN baseline."""

from fsl_train_common import parse_args, print_result, train


def main() -> int:
    report = train("tcn", parse_args(__doc__))
    print_result(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
