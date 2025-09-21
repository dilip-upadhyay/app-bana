import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'ab-button',
  standalone: true,
  imports: [CommonModule, MatButtonModule],
  templateUrl: './button.html',
  styleUrl: './button.css'
})
export class AbButton {
  @Input() color: 'primary' | 'accent' | 'warn' | undefined = undefined;
  @Input() disabled: boolean = false;
}
